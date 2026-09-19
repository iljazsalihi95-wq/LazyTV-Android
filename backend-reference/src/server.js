const {createApp}=require('./app'); const config=require('./config');
const {app}=createApp(); app.listen(config.port,()=>console.log(`LazyTV Activation backend listening on :${config.port}`));