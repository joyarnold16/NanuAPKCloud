const tabs=[["home","Home"],["rules","Rules"],["lights","Lights & Shapes"],["sound","Sound"],["iala","IALA"],["quiz","Quiz"]];
const rules=[
[1,"Application","Applies to vessels on the high seas and connected waters navigable by seagoing vessels."],
[2,"Responsibility","Nothing excuses neglect of the Rules, ordinary seamanship, dangers or special circumstances."],
[3,"General definitions","Defines vessel, power-driven, sailing, fishing, NUC, RAM, constrained by draught, underway and restricted visibility."],
[4,"Application of Section I","Rules 4 to 10 apply in any condition of visibility."],
[5,"Look-out","Maintain a proper look-out by sight, hearing and all available means."],
[6,"Safe speed","Proceed at a safe speed so effective avoiding action and stopping are possible."],
[7,"Risk of collision","Use all available means; constant bearing with decreasing range indicates risk."],
[8,"Action to avoid collision","Action must be positive, made in ample time and result in passing at a safe distance."],
[9,"Narrow channels","Keep near the outer limit on the starboard side and do not impede vessels limited to the channel."],
[10,"Traffic separation schemes","Use the correct lane, follow traffic flow, join or leave at small angles and avoid separation zones."],
[11,"Application of Section II","Rules 11 to 18 apply to vessels in sight of one another."],
[12,"Sailing vessels","Port tack keeps clear of starboard tack; windward keeps clear of leeward on the same tack."],
[13,"Overtaking","An overtaking vessel keeps out of the way until finally past and clear."],
[14,"Head-on situation","Both power-driven vessels alter course to starboard."],
[15,"Crossing situation","The vessel with the other on her starboard side keeps out of the way."],
[16,"Action by give-way vessel","Take early and substantial action to keep well clear."],
[17,"Action by stand-on vessel","Maintain course and speed initially, but act when the give-way vessel is not taking appropriate action."],
[18,"Responsibilities between vessels","Sets responsibilities among power-driven, sailing, fishing, NUC, RAM and constrained-by-draught vessels."],
[19,"Restricted visibility","Proceed at safe speed, engines ready, use radar properly and avoid port alterations for vessels forward of the beam where possible."],
[20,"Application of lights and shapes","Lights apply sunset to sunrise and in restricted visibility; shapes are displayed by day."],
[21,"Definitions","Defines masthead, sidelights, sternlight, towing light, all-round light and flashing light."],
[22,"Visibility of lights","Specifies minimum ranges for navigation lights by vessel length."],
[23,"Power-driven vessels underway","Show masthead light or lights, sidelights and sternlight."],
[24,"Towing and pushing","Specifies vertical masthead lights, towing light, sidelights, sternlight and shapes according to the tow."],
[25,"Sailing vessels and vessels under oars","Show sidelights and sternlight; sailing vessels may show red over green at the masthead."],
[26,"Fishing vessels","Trawling: green over white. Other fishing: red over white. Add gear-direction signals when required."],
[27,"NUC and RAM vessels","NUC: red over red and two balls. RAM: red-white-red and ball-diamond-ball."],
[28,"Constrained by draught","May show three all-round red lights or a cylinder in addition to power-driven vessel lights."],
[29,"Pilot vessels","White over red at or near the masthead, plus sidelights and sternlight when underway."],
[30,"Anchored and aground vessels","Anchor: all-round white light or lights and a ball. Aground adds red over red and three balls."],
[31,"Seaplanes","Where exact compliance is impracticable, show lights and shapes as closely similar as possible."],
[32,"Definitions of whistle signals","Defines short blast, prolonged blast and whistle terminology."],
[33,"Equipment for sound signals","Specifies whistle, bell and gong requirements by vessel length."],
[34,"Manoeuvring and warning signals","One short: starboard. Two short: port. Three short: astern propulsion. Five short: doubt."],
[35,"Sound signals in restricted visibility","Prescribes fog signals for making way, stopped, NUC, RAM, fishing, towing, anchor and aground vessels."],
[36,"Signals to attract attention","Any signal that cannot be mistaken for an authorised signal may be used to attract attention."],
[37,"Distress signals","Recognised signals include MAYDAY, red rockets, orange smoke and SOS."],
[38,"Exemptions","Allows limited exemptions for vessels built before specified dates."],
[39,"Verification definitions","Defines audit-related terms under the verification regime."],
[40,"Application of verification provisions","Applies verification provisions to Contracting Parties."],
[41,"Verification of compliance","Provides for IMO audits of Contracting Parties."]
];
const quiz=[
["Two power-driven vessels are nearly head-on. What should both do?",["Alter to port","Alter to starboard","Maintain course","Stop immediately"],1,"Rule 14: both alter course to starboard."],
["In a crossing situation the other vessel is on your starboard side. You are normally the…",["Stand-on vessel","Give-way vessel","Overtaking vessel","NUC vessel"],1,"Rule 15: keep out of the way."],
["A vessel is coming up from more than 22.5 degrees abaft your beam. It is…",["Crossing","Head-on","Overtaking","Not in sight"],2,"Rule 13."],
["What indicates a vessel not under command at night?",["Red over red","Green over white","Red over white","White over red"],0,"Rule 27: two all-round red lights."],
["What indicates a vessel engaged in trawling?",["Red over white","Green over white","White over red","Red-white-red"],1,"Rule 26."],
["Which day shape indicates a vessel at anchor?",["Cylinder","Diamond","Ball","Cone apex down"],2,"Rule 30."],
["What is the classic visual indication of collision risk?",["Increasing bearing","Constant bearing with decreasing range","Only CPA under one mile","Only radar alarm"],1,"Rule 7."],
["One short blast means…",["Altering to port","Altering to starboard","Astern propulsion","Doubt"],1,"Rule 34."],
["Five or more short rapid blasts mean…",["Overtaking","Danger or doubt","Anchored","Pilot vessel"],1,"Rule 34."],
["In fog, a power-driven vessel making way sounds…",["One prolonged blast at intervals not over two minutes","Two prolonged blasts every minute","One short blast","Bell only"],0,"Rule 35."],
["The stand-on vessel should initially…",["Always turn to port","Maintain course and speed","Stop engines","Sound three short blasts"],1,"Rule 17."],
["Red over white means…",["Fishing other than trawling","Pilot vessel","NUC","RAM"],0,"Rule 26."],
["White over red means…",["Fishing","Pilot vessel","Aground","Towing"],1,"Rule 29."],
["A RAM vessel shows at night…",["Red over red","Red-white-red","Green-white-green","White-red-white"],1,"Rule 27."],
["A vessel constrained by draught may show by day…",["Two cones","A cylinder","Ball-diamond-ball","Three balls"],1,"Rule 28."],
["Safe speed must allow the vessel to…",["Keep schedule","Take proper action and stop within an appropriate distance","Avoid radar","Always use half speed"],1,"Rule 6."],
["In a narrow channel a vessel should normally keep…",["To port","Near the starboard outer limit","In the centre","Outside the channel"],1,"Rule 9."],
["A sailing vessel on port tack should keep clear of…",["A sailing vessel on starboard tack","Any vessel to leeward only","A vessel under oars","An anchored vessel only"],0,"Rule 12."],
["An aground vessel adds which signals to anchor signals?",["Green over white and a cone","Red over red and three balls","White over red and diamond","Red-white-red and cylinder"],1,"Rule 30."],
["Rule 2 mainly reminds mariners that…",["Rules remove judgement","Neglect and special circumstances still matter","Only radar counts","Local rules never apply"],1,"Responsibility and ordinary seamanship remain essential."]
];
let qi=0,score=0,answered=0;
function init(){const n=document.getElementById("nav");tabs.forEach((x,i)=>{const b=document.createElement("button");b.textContent=x[1];b.className=i===0?"active":"";b.onclick=()=>show(x[0],b);n.appendChild(b)});renderRules();showQuestion()}
function show(id,b){document.querySelectorAll("section").forEach(s=>s.classList.toggle("active",s.id===id));document.querySelectorAll("nav button").forEach(x=>x.classList.remove("active"));if(b)b.classList.add("active");window.scrollTo(0,0)}
function renderRules(){const q=(document.getElementById("ruleSearch")?.value||"").toLowerCase();const box=document.getElementById("ruleList");box.innerHTML="";rules.filter(r=>String(r[0]).includes(q)||r[1].toLowerCase().includes(q)||r[2].toLowerCase().includes(q)).forEach(r=>{const d=document.createElement("div");d.className="card rule";d.innerHTML='<div><span class="num">Rule '+r[0]+'</span> — <b>'+r[1]+'</b></div><p>'+r[2]+'</p>';box.appendChild(d)})}
function solveScenario(){const v=document.getElementById("scenario").value;const m={cross:"You are the give-way vessel. Take early and substantial action, normally a clear alteration to starboard, and avoid crossing ahead.",head:"Both vessels should alter course to starboard and pass port-to-port.",overtake:"Keep out of the way until finally past and clear, regardless of any later change in bearing.",fog:"Use radar and all available means. Avoid an alteration to port for a vessel forward of the beam, except when overtaking; reduce speed if necessary."};document.getElementById("scenarioOut").textContent=m[v]}
function showQuestion(){const x=quiz[qi];document.getElementById("question").textContent=(qi+1)+". "+x[0];const a=document.getElementById("answers");a.innerHTML="";x[1].forEach((t,i)=>{const b=document.createElement("button");b.className="answer";b.textContent=t;b.onclick=()=>answer(i,b);a.appendChild(b)});document.getElementById("explain").textContent="";document.getElementById("nextBtn").style.display="none";document.getElementById("score").textContent="Score "+score+" / "+answered}
function answer(i,b){if(document.querySelector(".answer.correct,.answer.wrong"))return;answered++;const x=quiz[qi];document.querySelectorAll(".answer")[x[2]].classList.add("correct");if(i===x[2])score++;else b.classList.add("wrong");document.getElementById("explain").textContent=x[3];document.getElementById("score").textContent="Score "+score+" / "+answered;document.getElementById("nextBtn").style.display="inline-block";localStorage.setItem("rorScore",JSON.stringify({score,answered}))}
function nextQuestion(){qi=(qi+1)%quiz.length;showQuestion()}
init();
