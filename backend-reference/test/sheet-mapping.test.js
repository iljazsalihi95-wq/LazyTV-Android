const test=require('node:test'),assert=require('node:assert/strict');
const {mapStatus}=require('../src/sheet-repository');
test('PENDING maps to Android INACTIVE',()=>assert.equal(mapStatus({STATUS:'PENDING'}),'INACTIVE'));
test('ACTIVE future stays ACTIVE',()=>assert.equal(mapStatus({STATUS:'ACTIVE',END_AT:new Date(Date.now()+86400000).toISOString()}),'ACTIVE'));
test('ACTIVE past maps EXPIRED',()=>assert.equal(mapStatus({STATUS:'ACTIVE',END_AT:'01/01/2020 00.00.00'}),'EXPIRED'));
test('BLOCKED stays BLOCKED',()=>assert.equal(mapStatus({STATUS:'BLOCKED'}),'BLOCKED'));
test('explicit EXPIRED stays EXPIRED',()=>assert.equal(mapStatus({STATUS:'EXPIRED'}),'EXPIRED'));
