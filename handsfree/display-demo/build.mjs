import {build} from '../web-demo/node_modules/esbuild/lib/main.js';
import {mkdir,copyFile,cp} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(new URL('../web-demo/package.json',import.meta.url));
await mkdir('dist',{recursive:true});
await build({entryPoints:['src/display.ts'],bundle:true,outfile:'dist/display.js',format:'esm',platform:'browser',minify:true,plugins:[{name:'local-ward',setup(b){b.onResolve({filter:/^node:crypto$/},()=>({path:'crypto',namespace:'shim'}));b.onLoad({filter:/.*/,namespace:'shim'},()=>({contents:'export const randomUUID=()=>crypto.randomUUID();'}));b.onResolve({filter:/^zod$/},()=>({path:require.resolve('zod')}));}}]});
for(const f of ['index.html','display.html','display.css','host.js','host.css'])await copyFile(f,'dist/'+f);
await cp('../web-demo/public/vendor','dist/vendor',{recursive:true});
await copyFile('../web-demo/public/icon.svg','dist/icon.svg');

await copyFile('hospital.jpg','dist/hospital.jpg');await cp('vendor/wasd','dist/vendor/wasd',{recursive:true});
