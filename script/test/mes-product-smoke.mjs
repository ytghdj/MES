// Run against the acceptance database/backend only. No real credentials are recorded.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const base = process.env.MES_TEST_BASE || 'http://127.0.0.1:8082';
assert.equal(new URL(base).port, '8082');
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
const database = readFileSync('ruoyi-admin/target/mes-acceptance-db-name.txt', 'utf8').trim();
assert.match(database, /^mes_product_acceptance_\d+_\d+$/);
const clientId = readFileSync('plus-ui/.env.development', 'utf8').match(/^VITE_APP_CLIENT_ID\s*=\s*['"]?([^'"\r\n]+)/m)[1].trim();
const password = process.env.MES_TEST_PASSWORD || 'MesAcceptance2026!';
const checks = [];
const prefix = `TEST-${Date.now().toString(36)}-`;
const fixtureDir = 'ruoyi-modules/ruoyi-mes/target/acceptance-fixtures';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK1sAAAAASUVORK5CYII=', 'base64');

async function request(path, method = 'GET', body, token) {
  const headers = { clientid: clientId };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, { method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(120000) });
  if (response.headers.get('content-type')?.includes('json')) return await response.json();
  return Buffer.from(await response.arrayBuffer());
}
async function success(path, method, body, token) {
  const result = await request(path, method, body, token);
  assert.equal(result.code, 200, `${path}: ${result.msg}`);
  return result.data;
}
async function login(username) {
  return (await success('/auth/login', 'POST', { username, password, clientId, grantType: 'password' })).access_token;
}
const fileBody = (bytes, name, type = 'application/octet-stream') => {
  const form = new FormData(); form.append('file', new Blob([bytes], { type }), name); return form;
};
const product = code => ({ productCode: code, productName: ' 支架 ', specification: ' 10×20 ', materialGrade: '6061', drawingNo: '00002', customerPartNo: '00003', attachmentIds: '' });
const sql = query => execFileSync(process.env.MES_MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe', ['--defaults-extra-file=ruoyi-admin/target/mes-mysql.cnf', database, '--batch', '--skip-column-names', `--execute=${query}`], { encoding: 'utf8' }).trim();
const count = code => Number(sql(`SELECT COUNT(*) FROM mes_product WHERE product_code='${code}'`));

assert.equal((await request('/mes/product/list')).code, 401);
const admin = await login('admin');
const guest = await login('mes_guest');
assert.ok(admin && guest);
assert.equal((await request('/mes/product/list', 'GET', undefined, guest)).code, 403);
assert.equal((await request('/mes/product/attachment/upload', 'POST', fileBody(png, 'denied.png'), guest)).code, 403);
checks.push('anonymous and ungranted account rejected');
assert.ok(JSON.stringify(await success('/system/menu/getRouters', 'GET', undefined, admin)).includes('mes/product/index'));
checks.push('authorized menu and component path');

assert.notEqual((await request('/mes/product', 'POST', product(' '), admin)).code, 200);
assert.notEqual((await request('/mes/product', 'POST', product('x'.repeat(65)), admin)).code, 200);
const code = `${prefix}0001`;
await success('/mes/product', 'POST', { ...product(` ${code} `), status: '1', id: '999' }, admin);
let rows = (await success(`/mes/product/list?productCode=${code.toLowerCase()}`, 'GET', undefined, admin)).rows;
assert.equal(rows.length, 1);
let item = await success(`/mes/product/${rows[0].id}`, 'GET', undefined, admin);
assert.equal(item.productCode, code);
assert.equal(item.productName, '支架');
assert.equal(item.status, '0');
assert.equal(item.drawingNo, '00002');
assert.notEqual((await request('/mes/product', 'POST', product(code.toLowerCase()), admin)).code, 200);
checks.push('required fields, trim, lengths, default state and case-insensitive uniqueness');

const id = item.id;
const oldVersion = item.version;
await success('/mes/product', 'PUT', { ...item, productCode: `${prefix}EDIT`, specification: '', attachmentIds: '' }, admin);
item = await success(`/mes/product/${id}`, 'GET', undefined, admin);
assert.equal(item.id, id);
assert.equal(item.specification, '');
assert.notEqual((await request('/mes/product', 'PUT', { ...item, version: oldVersion }, admin)).code, 200);
await success('/mes/product/changeStatus', 'PUT', { id, status: '1', version: item.version }, admin);
item = await success(`/mes/product/${id}`, 'GET', undefined, admin);
assert.equal(item.status, '1');
assert.notEqual((await request('/mes/product/changeStatus', 'PUT', { id, status: '0', version: oldVersion }, admin)).code, 200);
assert.equal((await success(`/mes/product/list?productCode=${prefix}&status=0`, 'GET', undefined, admin)).rows.length, 0);
checks.push('fixed ID, optional field clearing, version conflict and status filtering');

const raceCode = `${prefix}RACE`;
const raced = await Promise.all([request('/mes/product', 'POST', product(raceCode), admin), request('/mes/product', 'POST', { ...product(raceCode.toLowerCase()), productName: '并发产品' }, admin)]);
assert.equal(raced.filter(result => result.code === 200).length, 1);
assert.equal(count(raceCode), 1);
checks.push('concurrent case-insensitive create leaves one database row');

assert.notEqual((await request('/mes/product/attachment/upload', 'POST', fileBody(Buffer.from('fake'), 'fake.pdf'), admin)).code, 200);
assert.notEqual((await request('/mes/product/attachment/upload', 'POST', fileBody(png, 'bad.exe'), admin)).code, 200);
const attachment = await success('/mes/product/attachment/upload', 'POST', fileBody(png, 'drawing.PNG', 'image/png'), admin);
await success('/mes/product', 'PUT', { ...item, attachmentIds: String(attachment.ossId) }, admin);
item = await success(`/mes/product/${id}`, 'GET', undefined, admin);
assert.equal(item.attachmentCount, 1);
assert.equal(item.attachments[0].fileName, 'drawing.PNG');
assert.deepEqual(await request(`/mes/product/${id}/attachment/${attachment.ossId}/preview`, 'GET', undefined, admin), png);
assert.deepEqual(await request(`/mes/product/${id}/attachment/${attachment.ossId}/download`, 'GET', undefined, admin), png);
assert.notEqual((await request(`/mes/product/${id}/attachment/99999/download`, 'GET', undefined, admin)).code, 200);
assert.equal((await request(`/mes/product/${id}/attachment/${attachment.ossId}/download`, 'GET', undefined, guest)).code, 403);
const files = [attachment];
for (let i = 0; i < 5; i++) files.push(await success('/mes/product/attachment/upload', 'POST', fileBody(png, `limit-${i}.png`, 'image/png'), admin));
assert.notEqual((await request('/mes/product', 'PUT', { ...item, attachmentIds: files.map(file => file.ossId).join(',') }, admin)).code, 200);
assert.equal((await success(`/mes/product/${id}`, 'GET', undefined, admin)).attachmentCount, 1);
const beforeDetach = await fetch('http://127.0.0.1:9900/__fixture/stats').then(response => response.json());
await success('/mes/product', 'PUT', { ...item, attachmentIds: '' }, admin);
item = await success(`/mes/product/${id}`, 'GET', undefined, admin);
assert.equal(item.attachmentCount, 0);
assert.deepEqual(await fetch('http://127.0.0.1:9900/__fixture/stats').then(response => response.json()), beforeDetach);
checks.push('file signature, uppercase extension, binding, preview/download bytes, authorization, limit and non-destructive detach');

const imported = await success('/mes/product/importData', 'POST', fileBody(readFileSync(`${fixtureDir}/valid.xlsx`), 'valid.xlsx'), admin);
assert.equal(imported.importedCount, 1);
assert.deepEqual(imported.errors, []);
let workbookProduct = (await success('/mes/product/list?productCode=000001', 'GET', undefined, admin)).rows[0];
assert.equal(workbookProduct.productCode, '000001');
assert.equal(workbookProduct.drawingNo, '00002');
assert.equal(workbookProduct.customerPartNo, '00003');
const duplicate = await success('/mes/product/importData', 'POST', fileBody(readFileSync(`${fixtureDir}/valid.xlsx`), 'duplicate.xlsx'), admin);
assert.equal(duplicate.importedCount, 0);
assert.ok(duplicate.errors.some(error => error.rowNumber === 2));
for (const name of ['invalid.xlsx', 'wrong-header.xlsx', 'over-limit.xlsx']) {
  const result = await success('/mes/product/importData', 'POST', fileBody(readFileSync(`${fixtureDir}/${name}`), name), admin);
  assert.equal(result.importedCount, 0); assert.ok(result.errors.length);
}
assert.equal(count('A'), 0);
checks.push('Excel leading zeros, duplicate rejection, row errors, header validation, max rows and zero partial writes');

const importRace = request('/mes/product/importData', 'POST', fileBody(readFileSync(`${fixtureDir}/race.xlsx`), 'race.xlsx'), admin);
let active = false;
for (let i = 0; i < 200; i++) {
  const activeCount = Number(sql(`SELECT COUNT(*) FROM information_schema.innodb_trx t JOIN information_schema.processlist p ON p.id=t.trx_mysql_thread_id WHERE p.db='${database}' AND t.trx_rows_modified>0`));
  if (activeCount > 0) { active = true; break; }
  await new Promise(resolve => setTimeout(resolve, 20));
}
assert.ok(active, 'import transaction did not become observable');
await success('/mes/product', 'POST', product('RACE-IMPORT'), admin);
const raceImportResult = await importRace;
assert.equal(raceImportResult.data.importedCount, 0);
assert.ok(raceImportResult.data.errors.length);
assert.equal(Number(sql("SELECT COUNT(*) FROM mes_product WHERE product_code LIKE 'RACE-BATCH-%'")), 0);
checks.push('real MySQL concurrent import conflict rolls back the whole batch');

const exported = await request('/mes/product/export?productCode=000001', 'POST', undefined, admin);
assert.ok(Buffer.isBuffer(exported) && exported.subarray(0, 2).toString() === 'PK');
writeFileSync('ruoyi-admin/target/mes-filtered-export.xlsx', exported);
const template = await request('/mes/product/importTemplate', 'POST', undefined, admin);
assert.ok(Buffer.isBuffer(template) && template.subarray(0, 2).toString() === 'PK');
checks.push('authorized filtered export and template download');
await success(`/mes/product/${id}`, 'DELETE', undefined, admin);
assert.equal((await success(`/mes/product/list?productCode=${prefix}EDIT`, 'GET', undefined, admin)).rows.length, 0);
assert.notEqual((await request('/mes/product', 'POST', product(`${prefix}EDIT`), admin)).code, 200);
assert.deepEqual(await fetch('http://127.0.0.1:9900/__fixture/stats').then(response => response.json()), beforeDetach);
checks.push('logical deletion reserves code and preserves stored objects');

const secondToken = await login('admin');
workbookProduct = await success(`/mes/product/${workbookProduct.id}`, 'GET', undefined, secondToken);
assert.equal(workbookProduct.productCode, '000001');
checks.push('fresh login reads persisted product');
const report = { database, storage: 'local S3 protocol fixture, not live MinIO', checks };
writeFileSync('ruoyi-admin/target/mes-smoke-result.json', JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
