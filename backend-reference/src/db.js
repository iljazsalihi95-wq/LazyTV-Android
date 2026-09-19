const fs=require('fs'), path=require('path'), Database=require('better-sqlite3');
const config=require('./config');
function openDatabase(file=config.databasePath){fs.mkdirSync(path.dirname(file),{recursive:true});const db=new Database(file);db.pragma('journal_mode = WAL'); db.pragma('foreign_keys = ON'); db.pragma('busy_timeout = 5000');return db;}
module.exports={openDatabase};