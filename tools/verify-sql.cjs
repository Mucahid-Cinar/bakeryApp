const fs = require('node:fs');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const source = fs.readFileSync('app/src/main/java/com/example/bakeryapp/DBHelper.java', 'utf8');
const statements = [...source.matchAll(/db\.execSQL\("([^"\n]+)"\)/g)].map(m => m[1]);
const reportSql = source.match(/List<StockRow> report\([\s\S]*?String sql="([^"\n]+)"/)[1].replace(' COLLATE LOCALIZED', '');
function schema() {
  const db = new DatabaseSync(':memory:');
  statements.filter(s => /^(CREATE TABLE|CREATE INDEX)/.test(s)).forEach(s => db.exec(s));
  return db;
}
function report(db, start, end) { return db.prepare(reportSql).all(start, end, start, end); }
function sums(rows) {
  return rows.reduce((t,r) => ({produced:t.produced+r.produced,sold:t.sold+r.sold}),{produced:0,sold:0});
}
let checks=0;
function check(name,body) { body(); checks++; console.log('PASS '+name); }
const db=schema();
const stock=db.prepare('INSERT INTO DailyStock VALUES(?,?,?,?,?)');
const sale=db.prepare('INSERT INTO SalesRecord(bread_type_id,date,quantity_sold) VALUES(?,?,?)');
db.exec("INSERT INTO BreadType(id,name,daily_total_quantity) VALUES(1,'Ekmek',100),(2,'Simit',100)");
stock.run(1,'2026-10-19','Francala',100,0);
stock.run(1,'2026-10-20','Köy Ekmeği',120,0);
sale.run(1,'2026-10-19',30);sale.run(1,'2026-10-19',20);sale.run(1,'2026-10-20',40);
check('multiple sales never multiply production',()=>{
  const rows=report(db,'2026-10-19','2026-10-20');
  assert.equal(rows.length,1);assert.equal(rows[0].produced,220);assert.equal(rows[0].sold,90);
});
check('rename aggregates one product and keeps old period title',()=>{
  assert.equal(report(db,'2026-10-19','2026-10-20')[0].name,'Köy Ekmeği');
  assert.equal(report(db,'2026-10-19','2026-10-19')[0].name,'Francala');
});
check('archiving and default production changes never rewrite report',()=>{
  db.exec("UPDATE BreadType SET name='Kepekli',daily_total_quantity=500,archived=1 WHERE id=1");
  const row=report(db,'2026-10-19','2026-10-20')[0];
  assert.equal(row.produced,220);assert.equal(row.sold,90);assert.equal(row.name,'Köy Ekmeği');
});
check('same name with different identity stays separate',()=>{
  stock.run(2,'2026-10-20','Köy Ekmeği',10,0);
  assert.equal(report(db,'2026-10-19','2026-10-20').length,2);
});
check('inclusive range excludes adjacent days and never invents production',()=>{
  assert.deepEqual(sums(report(db,'2026-10-19','2026-10-19')),{produced:100,sold:50});
  assert.equal(report(db,'2026-10-01','2026-10-18').length,0);
  assert.equal(report(db,'2026-10-21','2026-10-31').length,0);
});
check('new schema rejects negative and zero sale amounts',()=>{
  for(const n of [-1,0])assert.throws(()=>sale.run(1,'2026-10-20',n));
  assert.throws(()=>stock.run(1,'2026-10-21','A',-1,0));
});
const legacy=new DatabaseSync(':memory:');
legacy.exec("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER); CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER); INSERT INTO BreadType VALUES(1,'Ekmek',100),(2,'Pide',10); INSERT INTO SalesRecord VALUES(1,1,'2025-06-02',30),(2,99,'2025-06-02',5),(3,2,'2025-06-02',20),(4,2,'2025-06-03',0),(5,2,'2025-06-04',-2),(6,2,'2025-06-04',8)");
legacy.exec(statements.find(s=>s.startsWith('ALTER TABLE BreadType')));
statements.filter(s=>s.startsWith('CREATE TABLE DailyStock')||/^CREATE INDEX \w+ ON (SalesRecord|DailyStock)\(/.test(s)).forEach(s=>legacy.exec(s));
legacy.exec(statements.find(s=>s.startsWith('INSERT INTO DailyStock')));
check('migration preserves all sales including orphan products',()=>{
  assert.equal(legacy.prepare('SELECT COUNT(*) n FROM SalesRecord').get().n,6);
  const row=report(legacy,'2025-06-02','2025-06-02').find(r=>r.bread_type_id===99);
  assert.equal(row.name,'#99');assert.equal(row.produced,0);assert.equal(row.sold,5);assert.equal(row.estimated,1);assert.equal(row.inconsistent,1);
});
check('legacy oversold day stays flagged inside healthy looking period total',()=>{
  legacy.exec("UPDATE DailyStock SET produced=100,estimated=0 WHERE bread_type_id=2 AND date='2025-06-03'");
  const row=report(legacy,'2025-06-02','2025-06-03').find(r=>r.bread_type_id===2);
  assert.equal(row.produced,110);assert.equal(row.sold,20);assert.equal(row.inconsistent,1);
});
check('legacy negative transaction is flagged even when daily sum is positive',()=>{
  const row=report(legacy,'2025-06-04','2025-06-04').find(r=>r.bread_type_id===2);
  assert.equal(row.sold,6);assert.equal(row.inconsistent,1);
});
const randomDb=schema();
const randomStock=randomDb.prepare('INSERT INTO DailyStock VALUES(?,?,?,?,?)');
const randomSale=randomDb.prepare('INSERT INTO SalesRecord(bread_type_id,date,quantity_sold) VALUES(?,?,?)');
let seed=20261021;
function random(max){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed%max;}
const snapshots=[];
for(let id=1;id<=5;id++){
  randomDb.prepare('INSERT INTO BreadType(id,name,daily_total_quantity,archived) VALUES(?,?,?,?)').run(id,'Current '+id,999999,id===5?1:0);
  for(let d=1;d<=30;d++){
    if(random(4)===0)continue;
    const date='2026-10-'+String(d).padStart(2,'0');
    const produced=random(2001);let remaining=produced;let sold=0;
    for(let j=0;j<3;j++){const n=random(remaining+1);if(n>0){randomSale.run(id,date,n);sold+=n;remaining-=n;}}
    const name=(d<15?'Old ':'New ')+id;
    randomStock.run(id,date,name,produced,0);snapshots.push({id,date,name,produced,sold});
  }
}
check('200 deterministic date ranges match independent ledger totals',()=>{
  for(let sample=0;sample<200;sample++){
    const a=1+random(30),b=a+random(31-a);
    const start='2026-10-'+String(a).padStart(2,'0'),end='2026-10-'+String(b).padStart(2,'0');
    const expected=new Map();
    for(const row of snapshots.filter(r=>r.date>=start&&r.date<=end)){
      const value=expected.get(row.id)||{produced:0,sold:0,name:'',date:''};
      value.produced+=row.produced;value.sold+=row.sold;
      if(row.date>value.date){value.date=row.date;value.name=row.name;}
      expected.set(row.id,value);
    }
    const actual=report(randomDb,start,end);
    assert.equal(actual.length,expected.size);
    for(const row of actual){const e=expected.get(row.bread_type_id);assert.equal(row.produced,e.produced);assert.equal(row.sold,e.sold);assert.equal(row.name,e.name);assert.equal(row.inconsistent,0);assert.equal(row.estimated,0);assert.ok(row.produced>=row.sold);}
  }
});
check('weekly totals equal the sum of seven individual days',()=>{
  const whole=sums(report(randomDb,'2026-10-19','2026-10-25'));
  const days={produced:0,sold:0};
  for(let d=19;d<=25;d++){const value=sums(report(randomDb,'2026-10-'+d,'2026-10-'+d));days.produced+=value.produced;days.sold+=value.sold;}
  assert.deepEqual(whole,days);
});
check('SQL aggregates safely beyond 32 bit integer range',()=>{
  randomStock.run(6,'2026-10-01','Large',2147483647,0);randomStock.run(6,'2026-10-02','Large',2147483647,0);
  assert.equal(report(randomDb,'2026-10-01','2026-10-02').find(r=>r.bread_type_id===6).produced,4294967294);
});
[db,legacy,randomDb].forEach(d=>d.close());
console.log(`${checks} SQL checks passed, including 200 ledger comparisons.`);
