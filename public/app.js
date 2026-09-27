const CONTACTS={
  whatsapp:"",
  telegram:"",
  email:""
};

const header=document.getElementById("header");
const nav=document.getElementById("nav");
const menu=document.getElementById("menu");
const toast=document.getElementById("toast");

document.getElementById("year").textContent=new Date().getFullYear();

const syncHeader=()=>header.classList.toggle("scrolled",window.scrollY>16);
syncHeader();
window.addEventListener("scroll",syncHeader,{passive:true});

menu.addEventListener("click",()=>{
  nav.classList.toggle("open");
  menu.textContent=nav.classList.contains("open")?"×":"☰";
});

nav.querySelectorAll("a").forEach(a=>a.addEventListener("click",()=>{
  nav.classList.remove("open");
  menu.textContent="☰";
}));

const observer=new IntersectionObserver(entries=>{
  entries.forEach(entry=>{
    if(entry.isIntersecting){
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    }
  });
},{threshold:.12});

document.querySelectorAll(".reveal").forEach((el,i)=>{
  el.style.transitionDelay=`${Math.min((i%4)*70,210)}ms`;
  observer.observe(el);
});

function showToast(message){
  toast.textContent=message;
  toast.classList.add("show");
  clearTimeout(window.toastTimer);
  window.toastTimer=setTimeout(()=>toast.classList.remove("show"),3200);
}

document.querySelectorAll("[data-contact]").forEach(btn=>{
  btn.addEventListener("click",()=>{
    const url=CONTACTS[btn.dataset.contact];
    if(url){
      window.open(url,"_blank","noopener,noreferrer");
      return;
    }
    showToast("Канал связи подготовлен. Осталось добавить реальный номер, Telegram и e-mail в конфигурацию сайта.");
  });
});