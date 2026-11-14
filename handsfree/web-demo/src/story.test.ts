import {test} from 'node:test';import assert from 'node:assert/strict';
import {slides,duration,positionAt,startOf} from './story';
test('seeking and playback cover the complete film without gaps or a loop at the end',()=>{
 assert.equal(positionAt(0).index,0);for(let i=1;i<slides.length;i++){const boundary=startOf(i);assert.equal(positionAt(boundary-.001).index,i-1);assert.equal(positionAt(boundary).index,i);assert.equal(positionAt(boundary).progress,0)}assert.equal(positionAt(duration).index,slides.length-1);assert.equal(positionAt(duration+100).progress,1);assert.equal(duration,115);
});
test('script keeps documentation review before save and distinguishes the emergency patient',()=>{
 const ids=slides.map(s=>s.id);assert.ok(ids.indexOf('dictate')<ids.indexOf('review'));assert.ok(ids.indexOf('review')<ids.indexOf('saved'));assert.ok(ids.indexOf('saved')<ids.indexOf('code-blue'));assert.match(slides.find(s=>s.id==='review')!.lines!.join(' '),/Alex Morgan · Room 7/);assert.match(slides.find(s=>s.id==='code-blue')!.lines!.join(' '),/Room 8/);assert.match(slides.find(s=>s.id==='dictate')!.hint!,/not saved/);
});
