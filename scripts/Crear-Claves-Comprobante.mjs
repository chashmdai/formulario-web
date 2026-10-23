import fs from 'node:fs';import path from 'node:path';import {generateKeyPairSync,randomBytes} from 'node:crypto';
const directory=path.resolve(process.argv[2]);
if(!directory.endsWith(path.join('.local','robustez','recepcion')))throw new Error('Directorio de claves fuera del laboratorio');
fs.mkdirSync(directory,{recursive:true});
const institutional=process.argv.includes('--institucional');
const privatePath=path.join(directory,institutional?'institution-private.pem':'receipt-private.pem'),publicPath=path.join(directory,institutional?'institution-trust.json':'receipt-trust.json');
if(fs.existsSync(privatePath)!==fs.existsSync(publicPath))throw new Error('Par de claves incompleto. Restaurar respaldo; no regenerar automáticamente');
if(!fs.existsSync(privatePath)){
 const keys=generateKeyPairSync('ed25519');
 fs.writeFileSync(privatePath,keys.privateKey.export({type:'pkcs8',format:'pem'}),{flag:'wx'});
 fs.writeFileSync(publicPath,JSON.stringify({[institutional?'institution-ed25519-v1':'local-ed25519-v1']:keys.publicKey.export({type:'spki',format:'pem'})},null,2),{flag:'wx'});
}
// No imprime claves, tokens ni huellas de datos ciudadanos.
console.log('Claves locales de comprobante disponibles. Conservar la privada junto con los respaldos.');
