import {build} from 'esbuild';
import {mkdir,copyFile,cp} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
await mkdir('dist',{recursive:true});
await build({entryPoints:['src/main.ts'],bundle:true,outfile:'dist/app.js',format:'esm',platform:'browser',minify:true,plugins:[{name:'browser-core',setup(b){b.onResolve({filter:/^node:crypto$/},()=>({path:'browser-crypto',namespace:'shim'}));b.onLoad({filter:/.*/,namespace:'shim'},()=>({contents:'export const randomUUID=()=>crypto.randomUUID();'}));b.onResolve({filter:/^zod$/},()=>({path:require.resolve('zod')}));}}]});
await copyFile('public/patient-a.svg','dist/patient-a.svg');await copyFile('public/patient-b.svg','dist/patient-b.svg');
await copyFile('index.html','dist/index.html');await copyFile('src/style.css','dist/style.css');

await build({entryPoints:['src/film.ts'],bundle:true,outfile:'dist/film.js',format:'esm',platform:'browser',minify:true});
await copyFile('src/film.css','dist/film.css');
await copyFile('playground.html','dist/playground.html');
await cp('public/vendor','dist/vendor',{recursive:true});

await copyFile('public/wristband-qr.png','dist/wristband-qr.png');
