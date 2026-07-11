const weddingDate = new Date('2026-08-22T15:00:00+05:00');
const parts = { days: document.querySelector('#days'), hours: document.querySelector('#hours'), minutes: document.querySelector('#minutes'), seconds: document.querySelector('#seconds') };
function updateCountdown(){const diff=Math.max(0,weddingDate-Date.now());const d=Math.floor(diff/86400000);const h=Math.floor(diff/3600000)%24;const m=Math.floor(diff/60000)%60;const s=Math.floor(diff/1000)%60;parts.days.textContent=d;parts.hours.textContent=String(h).padStart(2,'0');parts.minutes.textContent=String(m).padStart(2,'0');parts.seconds.textContent=String(s).padStart(2,'0')}
updateCountdown();setInterval(updateCountdown,1000);

const observer=new IntersectionObserver(entries=>entries.forEach(e=>{if(e.isIntersecting)e.target.classList.add('visible')}),{threshold:.12});
document.querySelectorAll('.reveal').forEach(el=>observer.observe(el));

const slug=location.pathname.startsWith('/i/')?location.pathname.split('/').filter(Boolean)[1]:null;
const greeting=document.querySelector('#personalGreeting');
const form=document.querySelector('#rsvpForm');
const hint=document.querySelector('#rsvpHint');
const message=document.querySelector('#formMessage');

if(slug){
  fetch(`/api/invite/${slug}`).then(async r=>{if(!r.ok)throw new Error('Приглашение не найдено');return r.json()}).then(invite=>{
    greeting.innerHTML=`<strong>${invite.greeting}, ${invite.guestNames}!</strong><br>Это приглашение создано специально для вас.`;
    greeting.classList.remove('hidden');form.classList.remove('hidden');hint.textContent='Пожалуйста, сообщите, сможете ли вы разделить с нами этот день.';
    if(invite.response){
      form.firstName.value=invite.response.firstName||'';form.lastName.value=invite.response.lastName||'';
      const radio=form.querySelector(`[name="attendance"][value="${invite.response.attendance}"]`);if(radio)radio.checked=true;
      message.textContent='Ваш ответ уже сохранён. При необходимости его можно изменить и отправить снова.';
    }
  }).catch(err=>{hint.textContent=err.message});
}

form?.addEventListener('submit',async e=>{
  e.preventDefault();message.textContent='Отправляем…';
  const data=Object.fromEntries(new FormData(form).entries());
  const r=await fetch(`/api/invite/${slug}/rsvp`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
  const result=await r.json();
  if(!r.ok){message.textContent=result.error||'Не удалось отправить ответ';return}
  message.textContent='Спасибо! Мы получили ваш ответ ♥';
});
