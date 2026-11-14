import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
const types={'.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.glb':'model/gltf-binary','.txt':'text/plain','.html':'text/html'};
const port=Number(process.env.PORT||8800);
createServer(async(req,res)=>{try{const path=decodeURIComponent(new URL(req.url,'http://localhost').pathname);if(path.includes('..'))throw 0;const file=path==='/'?'index.html':path.slice(1);const data=await readFile(new URL('./dist/'+file,import.meta.url));const ext=file.slice(file.lastIndexOf('.'));res.setHeader('Content-Type',types[ext]||'application/octet-stream');res.setHeader('Cache-Control','no-cache');res.end(data)}catch{res.writeHead(404).end('Not found')}}).listen(port,'127.0.0.1',()=>console.log(`Lumi demo http://127.0.0.1:${port}`));
