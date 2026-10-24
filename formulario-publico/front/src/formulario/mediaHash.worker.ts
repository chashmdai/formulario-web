import {sha256} from '@noble/hashes/sha2.js';
import {bytesToHex} from '@noble/hashes/utils.js';
self.onmessage=async(event:MessageEvent<{file:File;chunk:number}>)=>{
 try{const {file,chunk}=event.data;if(chunk<1||chunk>8*1024*1024)throw new Error();const full=sha256.create(),blocks:string[]=[];
  for(let start=0;start<file.size;start+=chunk){const bytes=new Uint8Array(await file.slice(start,start+chunk).arrayBuffer());full.update(bytes);blocks.push(bytesToHex(sha256(bytes)));self.postMessage({read:Math.min(file.size,start+chunk)});}
  self.postMessage({digest:bytesToHex(full.digest()),blocks});
 }catch{self.postMessage({error:true});}
};
