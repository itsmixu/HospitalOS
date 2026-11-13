# Smart Glasses Hackathon Projects, Indie Apps and Tech Demos (2023-2026)

## Which projects have hackers and indie developers built on Meta Ray-Ban / Ray-Ban Display glasses?

### Takeaway
The best-known projects fall into a few groups: (1) always-on agents that act for you (VisionClaw, Super); (2) memory and recall tools (Yaad, recall-glasses, FriendFinder, OpenGlass); (3) step-by-step task guidance (Signoff, cooking apps, Firsthand); (4) translation and subtitles; (5) fitness and HUD utilities on the Display (Ghost Run); and (6) provocative "dark" demos (I-XRAY, chess cheating). Agent and memory projects drew the most traction and press.

### Cited Findings

**VisionClaw (agentic assistant, open source, 2026)**
- It runs on display-less Ray-Ban Meta glasses. A custom iPhone app streams camera and mic frames to Gemini Live, which answers by voice or hands tasks to OpenClaw (browser, email, calendar, web search). The authors are from CU Boulder, GIST and Google. — [The Decoder](https://the-decoder.com/always-on-ray-ban-meta-glasses-powered-by-openclaw-speed-up-everyday-tasks-in-new-study/)
- Study 1 (N=12): tasks were 13-37% faster and rated 7-46% less demanding than the baselines, with similar success rates. Note-taking success fell to about 58% because the camera struggled with small text such as receipts. — [The Decoder](https://the-decoder.com/always-on-ray-ban-meta-glasses-powered-by-openclaw-speed-up-everyday-tasks-in-new-study/)
- Field study (the 4 authors, 55 participant-days): 555 voice interactions. Usage split was info retrieval 30%, shopping 19%, saving content 16%, communication 14%, remembering 12%, control 9%. Usefulness grew as personal data accumulated. — [The Decoder](https://the-decoder.com/always-on-ray-ban-meta-glasses-powered-by-openclaw-speed-up-everyday-tasks-in-new-study/); paper: [arXiv 2604.03486](https://arxiv.org/html/2604.03486)
- Repo is github.com/sseanliu/VisionClaw (Sean Liu), also mirrored under Intent-Lab. It is iOS/Swift on the Meta Wearables DAT. Snapshots show between about 1.3k and 2.4k stars, so the count is inconsistent. — [Suganthan blog](https://suganthan.com/blog/visionclaw-openclaw-meta-glasses/); [Intent Lab GitHub](https://github.com/Intent-Lab)
- Road to VR called the ClawdBot/OpenClaw-on-glasses hack a "massive utility boost" but "risky", mainly because it gives an autonomous agent account access. — [Road to VR](https://www.roadtovr.com/clawdbot-ray-ban-smart-glasses-hack/)

**I-XRAY (Harvard, Oct 2024; privacy demo)**
- AnhPhu Nguyen and Caine Ardayfio streamed video from Ray-Ban Meta to PimEyes face search. An LLM then compiled names, addresses and phone numbers from articles and data brokers such as FastPeopleSearch. — [404 Media](https://www.404media.co/someone-put-facial-recognition-tech-onto-metas-smart-glasses-to-instantly-dox-strangers); [Boston Globe](https://www.bostonglobe.com/2024/10/04/business/harvard-students-ai-meta-glasses/)
- They did not release the code. They framed the demo as awareness and published opt-out instructions for PimEyes and people-search sites. Meta replied that any camera could be used the same way. — [IDTechWire](https://idtechwire.com/harvard-students-connect-meta-ray-bans-to-pimeyes-face-search-provoking-privacy-concerns); [NY Sun](https://nysun.com/article/dark-side-of-smart-glasses-students-show-how-they-can-instantly-find-strangers-names-addresses)

**OpenGlass (Meta Llama 3 Hackathon, SF, 2024): 1st place**
- A roughly $20 open-source clip-on that makes any glasses an AI wearable. You can ask about what you see now or saw earlier; the demo read the calories of berries in view. A 5-person team led by Nik Shevchenko (Based Hardware) won among 51 projects built in 24h, even though speech-to-text bugged out during the demo. — [Swarajya](https://swarajyamag.com/technology/openglass-built-over-24-hours-at-meta-hackathon-these-open-source-ai-smart-glasses-might-give-ray-ban-meta-a-run-for-its-money); [Quartz](https://qz.com/a-group-of-hackers-built-20-open-source-smart-glasses-1851478685); [Meta recap](https://ai.meta.com/blog/llama-3-hackathon-recap-cerebral-valley/)

**Chess-cheat glasses (TeenHacks LI, 2025)**
- Eddie/Eddy Xu modified a Ray-Ban Meta setup to send the board view to Stockfish, which returns suggested moves. It went viral. — [dev.ua](https://dev.ua/en/news/chity-dlia-shakhiv-1736958727)
- A later viral TikTok (SourGrape02, Oct 2026) showed a similar GPT + Stockfish pipeline. One analysis calls the setup unverified. — [shattered.io](https://shattered.io/ray-ban-meta-smart-glasses-chess-cheating-eddy-xu-2026/)

**Meta Global AI Developer Hackathon (Cerebral Valley, Oct-Nov 2026; Muse Spark + glasses)**
- **Yaad**: memory aid for dementia. It identifies objects through Meta glasses and stores sightings, so you can ask "where did I last see my keys?". It appears on a gallery page titled "WINNERS", but its placement is not stated. Repo: github.com/Aastik0/Yaad. — [Cerebral Valley](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/hackathon/gallery/3379)
- **Signoff**: breaks a physical task (furniture assembly, a fried egg) into steps, each with a "done right" check. Muse Spark verifies each step from a glasses photo and returns pass, fail or unsure. It judged 29 of 30 test photos correctly and signed off none of 14 wrong steps. — [Cerebral Valley](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/hackathon/gallery/2748)
- **Portravo** (hands-free construction quoting on Ray-Ban Display) and **Den Lens / Moltbot Den** (sourced Q&A on Ray-Ban Display, filmed on real hardware). Also **Firsthand** (first-person video turned into step guides; its glasses footage was simulated) and **ISee** (vision guidance, tested on real Ray-Ban Meta). — [CV gallery search results](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/hackathon/gallery/3725)
- Other entries from that hackathon in the community index: road-to-5k (couch-to-5K coach) and athena (camera-verified skills marketplace). — [awesome-meta-ray-ban-display-apps](https://github.com/Javamomma/awesome-meta-ray-ban-display-apps)

**Community index (693 repos in late 2026), selected everyday-use projects** — [awesome-meta-ray-ban-display-apps](https://github.com/Javamomma/awesome-meta-ray-ban-display-apps)
- Memory: **recall-glasses** (Umang3211) remembers the people you meet and won 1st place at JacHacks SF 2026. **FriendFinder** reminds you of friends' names and details. **realityos-spatial-memory** is a visual database you query by voice to find objects and places.
- Cooking: CookingGlasses, gulzar (step-by-step making with voice), RecipeGuide.
- Translation: kiwi-meta-glasses-ios (bilingual subtitles plus a 14-tool voice agent), gaeilge-live (Irish-English), borderless.
- Navigation: waypoint, MetaDisplayNav (OpenStreetMap), meta-travel-deck (walking directions and live transit).
- Accessibility and health: labellens (reads food and medication labels and gives a good/caution/avoid verdict), fraudlens (scam prevention), bhavya-ai-app (autism companion), Nudge-wearable (cuts phone-reaching).
- Hackathon misc: "Watch Me" (meta-hackathon-2026), a voice-locked personal-safety check-in that sends contacts a map link. lookgrab: look at an object and pinch, and a robot arm fetches it. GeminiDisplayKit, from the Google I/O Hackathon 2026.
- Most-starred: the official DAT SDKs (iOS 577, Android 386), turbometa-rayban-ai (548, Chinese assistant), OpenGlasses (124, non-Meta models plus agents), kinesis (129, Neural Band controls for macOS).

**Ray-Ban Display HUD apps (UploadVR, Jun 2026)** — [UploadVR](https://www.uploadvr.com/meta-ray-ban-display-amazing-hud-apps/)
- Ghost Run (Stijn Spanhove, In The Pocket): race your past Strava run using the glasses compass and phone GPS.
- Super (Rohan Arun): agent that ordered Starbucks by voice alone, with no phone.
- Also: smart-home control by Neural Band swipes (Krzysztof Wrona), DOOM port, and YouTube/TikTok/Twitch clients plus a reader-mode browser (AeroSummit, via the Herald Hub launcher).

**Other ecosystems (useful precedents)**
- AugmentedChords (Kevin Lin) showed sheet music in the glasses while playing piano. It won Mentra's first hackathon (Mar 2025, Even Realities G1) and was posted as a Show HN. — [GitHub](https://github.com/kevinlinxc/AugmentedChords); [HN](https://news.ycombinator.com/item?id=43876243)
- RizzGPT ("charisma as a service"): students built a real-time conversation-cue assistant on Brilliant Labs glasses. — [Freethink](https://www.freethink.com/robots-ai/frame-ai)
- Partner integrations named for the DAT: Microsoft Seeing AI, Be My Eyes, Disney Imagineering park guide, 18Birdies golf, Twitch/Streamlabs POV streaming. — [Nervegna Substack](https://nervegna.substack.com/p/metas-wearables-developer-center)
- Before the SDK existed, developers bridged the glasses through messaging. One wrote a WhatsApp server to intercept messages so the glasses could reach custom apps. — [jschrier blog](https://jschrier.github.io/blog/2024/07/18/Hacking-Meta-Rayban-Glasses.html)

### Inferences
- Memory ("where did I put X", "who is this person I met") and step verification are the recurring winning concepts across 2024-2026. Both use the camera's always-there, first-person view in ways a phone can't match.
- Projects that show a concrete metric (Signoff's 29/30, VisionClaw's % faster) or a filmed demo on real hardware stand out from projects that simulate glasses footage.

### Gaps
- I found no direct coverage of a "Ray-Ban Meta + GPT-4o via Messenger/WhatsApp video call" hack. Only the official in-call camera sharing feature and the WhatsApp-bridge blog turned up.
- I found no notable dedicated shopping price-checker, plant-ID or social-cue project on Ray-Ban Meta, beyond RizzGPT on Brilliant Labs and labellens.
- The Cerebral Valley hackathon's official placements were not confirmed beyond the "WINNERS" page title.

## Which Meta-sponsored or wearables hackathons and challenges have run, and who won?

### Takeaway
Confirmed Meta-glasses winners: SmartSight (Gold Best in Hack, also the Meta DAT track) and OurLife at MIT Reality Hack 2026; OpenGlass at the Meta Llama 3 hackathon (2024); Yaad (likely) at the Meta Global AI Developer Hackathon 2026; and recall-glasses at JacHacks SF 2026. I could not find details on past Junction Meta challenges.

### Cited Findings
- **MIT Reality Hack 2026**: the sponsor prize was "Best Use of the Meta Wearables Device Access Toolkit Showcasing a Hands-free Everyday Use Case". SmartSight and OurLife were co-winners ($1,500 cash, 2 winners). SmartSight also won the Gold Prize for Best in Hack. — [Reality Hack prizes](https://www.realityhackatmit.com/2026-tracks-prizes); [Devpost](https://reality-hack-2026.devpost.com/)
- Reality Hack 2026 Snap Spectacles winners: Noodle (Spatial AI), CareNest (AR bottle tracking for sleep-deprived parents; Snap Cloud), SenSee (hardware integration). Raven Glasses winner: R-EYE-DER. — [Reality Hack prizes](https://www.realityhackatmit.com/2026-tracks-prizes)
- **Meta Connect Web Apps Creator Hackathon (Ray-Ban Display, Devpost, 2026)**: judging focused on personal impact, whether the project works better on glasses than on a phone, and how quickly it delivers value. Winners were "to be announced" when checked; the page returned 403 to direct fetch. — [Devpost](https://meta-connect-web-apps.devpost.com/) (via search snippet)
- **Meta Global AI Developer Hackathon (Cerebral Valley, Oct-Nov 2026)**: Muse Spark, with many glasses entries (see above). — [Cerebral Valley](https://cerebralvalley.ai/e/meta-ai-developer-hackathon/hackathon/gallery/3379)
- **Meta Llama 3 Hackathon (SF, 2024)**: OpenGlass took 1st of 51. — [Meta AI blog](https://ai.meta.com/blog/llama-3-hackathon-recap-cerebral-valley/)
- HackHarvard 2025 gave Ray-Ban Meta glasses as the 3rd-place prize. HackTX 2026 had a Meta-sponsored track with Ray-Ban Meta prizes. — [HackHarvard Devpost](https://hackharvard-2025.devpost.com/); [HackTX 2026](https://hacktx26.devpost.com/)
- **Mentra/AugmentOS**: first hackathon at Mentra HQ in Mar 2025, won by AugmentedChords. A Mentra Live hackathon at YC gave away glasses. — [GitHub](https://github.com/kevinlinxc/AugmentedChords); [Luma](https://luma.com/jpjpjyta)
- **Snap Lens Fest 2025 (Oct 16)**: Best Spectacles Lens went to "Card Master" (Harry Banda). Fireside Tales, collaborative GenAI storytelling, placed 3rd in the Spectacles Lensathon. — [Lenslist recap](https://blog.lenslist.co/?p=24901); [Voices of VR](https://voicesofvr.com/1674-3rd-place-spectacles-lensathon-team-fireside-tales-collaborative-storytelling-with-genai/)
- Wearables Device Access Toolkit 1.0 launched at Connect 2026. The Ray-Ban Display developer preview opened May 14, 2026, with a native SDK or plain HTML/CSS/JS web apps. — [Meta Connect 2026 recap](https://developers.meta.com/blog/meta-connect-recap/); [vr.org](https://vr.org/articles/meta-ray-ban-display-developer-sdk-2026)

### Inferences
- The Reality Hack Meta track wording ("hands-free everyday use case") nearly matches the Junction 2026 brief. SmartSight winning both the track and Best in Hack suggests an everyday, accessibility-flavoured assistive concept done well can beat flashier XR entries.

### Gaps
- I found no public record of past Junction Meta challenges or their winners. The Junction Wikipedia entry has nothing on it.
- I found no descriptions of SmartSight or OurLife (Devpost returned 403).
- I found no Brilliant Labs Frame hackathon winners; the site mentions a hackathon with no results.

## What did judges reward, and what resonated with audiences?

### Takeaway
Judges and audiences rewarded: a clear everyday problem that is better on glasses than on a phone, a live demo on real hardware (polish matters, but OpenGlass won despite a bug), measurable proof that it works, and a human or accessibility angle. Viral reach went to provocative demos (I-XRAY, chess cheating) and "the glasses act for me" agent clips.

### Cited Findings
- Ray-Ban Display hackathon criteria: personal impact, better on glasses than on a phone, speed of value. — [Devpost](https://meta-connect-web-apps.devpost.com/)
- OpenGlass won on its live demo plus cheap, open hardware, despite a speech-to-text bug on stage. — [Swarajya](https://swarajyamag.com/technology/openglass-built-over-24-hours-at-meta-hackathon-these-open-source-ai-smart-glasses-might-give-ray-ban-meta-a-run-for-its-money)
- Design lessons for DAT apps: audio is the main output, so keep it short. "Silence is a feature": log observations and speak only when it matters. Time interruptions (e.g., between reps). Let the phone do the compute. Privacy is social: bystander trust signals matter. Limits as of then: no HUD access via DAT, no Meta AI integration, BT bandwidth and battery constraints. MockDeviceKit lets you prototype without hardware. — [Nervegna Substack](https://nervegna.substack.com/p/metas-wearables-developer-center)
- Ray-Ban Display HUD constraints: a 600×600 HUD driven by Neural Band swipes; web apps can't hold API keys, open the camera, or call Gemini directly. — [Gemini Display Kit, CV gallery](https://cerebralvalley.ai/e/google-io-hackathon/hackathon/gallery/56)
- Real-world note: VisionClaw's screenless use was less obtrusive but sometimes less reliable, and reading small text from the camera often failed. — [The Decoder](https://the-decoder.com/always-on-ray-ban-meta-glasses-powered-by-openclaw-speed-up-everyday-tasks-in-new-study/)
- Viral content: I-XRAY and both chess-cheat clips got wide press; Super's voice-only Starbucks order was highlighted by UploadVR. — [404 Media](https://www.404media.co/someone-put-facial-recognition-tech-onto-metas-smart-glasses-to-instantly-dox-strangers); [shattered.io](https://shattered.io/ray-ban-meta-smart-glasses-chess-cheating-eddy-xu-2026/); [UploadVR](https://www.uploadvr.com/meta-ray-ban-display-amazing-hud-apps/)

### Inferences
- For Junction: film the demo on real glasses, show one quantified validation (as Signoff did), and state in one sentence why it needs glasses rather than a phone (hands busy, eyes on the task, first-person context).
- Assistive and memory use cases (dementia, low vision, recall of names) match both the judging tone and Meta's own partner choices (Be My Eyes, Seeing AI).

### Gaps
- I found no published rubrics or judge commentary for SmartSight or OurLife.

## Cautionary examples and how good projects handled consent

### Takeaway
Face recognition of strangers (I-XRAY), covert recording (LED tampering), and human review of glasses data are the main controversies. Good projects avoid identifying bystanders, keep processing and memory scoped to the user's own objects and tasks, rely on the visible capture LED, and are explicit about what gets stored.

### Cited Findings
- I-XRAY: PimEyes face search plus LLM doxxing. The creators withheld the code and published opt-out guides. Bruce Schneier called it "unsurprising". — [IDTechWire](https://idtechwire.com/harvard-students-connect-meta-ray-bans-to-pimeyes-face-search-provoking-privacy-concerns)
- A tinkerer reportedly disabled the glasses' recording LED, raising covert-recording concerns (OECD AI incident log, Oct 2025). — [OECD.AI](https://oecd.ai/en/incidents/2025-10-24-ad63)
- Reports in 2026 said subcontractors (including in Kenya) reviewed intimate Meta AI visual queries from glasses, which fed a privacy backlash. — [UploadVR](https://uploadvr.com/subcontractors-see-intimate-meta-ai-visual-queries-from-smart-glasses); [AppleInsider](https://appleinsider.com/articles/26/03/03/what-privacy-as-expected-meta-ray-bans-are-a-privacy-disaster)
- A reported case of a victim filmed and extorted via smart glasses. — [Digital Trends](https://digitaltrends.com/wearables/the-privacy-nightmare-of-smart-glasses-turns-real-as-victim-filmed-and-extorted)
- VisionClaw's authors flag risks from constant recording and large volumes of personal data. Road to VR called agent account access "risky". — [The Decoder](https://the-decoder.com/always-on-ray-ban-meta-glasses-powered-by-openclaw-speed-up-everyday-tasks-in-new-study/); [Road to VR](https://www.roadtovr.com/clawdbot-ray-ban-smart-glasses-hack/)
- The awesome-list warns users to review code before granting API keys or phone/desktop bridges to hackathon projects. — [awesome-meta-ray-ban-display-apps](https://github.com/Javamomma/awesome-meta-ray-ban-display-apps)

### Inferences
- People-memory apps (recall-glasses, FriendFinder) sit close to I-XRAY territory. A safer framing is opt-in only: remember people the user explicitly saves, no web lookup of strangers. Object and task memory (Yaad, Signoff) carries much less risk.
- Mentioning bystander-safe design in the pitch (event-triggered capture instead of constant recording, local or ephemeral storage, no face ID) is likely to score with Meta judges given the 2024-2026 backlash.

### Gaps
- I found no hackathon winner that explicitly documented a consent mechanism such as on-device processing or opt-in bystander handling.
