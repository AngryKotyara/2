const header = document.getElementById("header");
const menuButton = document.getElementById("menuButton");
const mobileMenu = document.getElementById("mobileMenu");
const glow = document.querySelector(".cursor-glow");

const updateHeader = () => {
  header.classList.toggle("scrolled", window.scrollY > 18);
};
updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

menuButton.addEventListener("click", () => {
  const open = mobileMenu.classList.toggle("open");
  menuButton.classList.toggle("open", open);
  menuButton.setAttribute("aria-expanded", String(open));
});

mobileMenu.querySelectorAll("a").forEach(link => {
  link.addEventListener("click", () => {
    mobileMenu.classList.remove("open");
    menuButton.classList.remove("open");
    menuButton.setAttribute("aria-expanded", "false");
  });
});

const observer = new IntersectionObserver(entries => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12, rootMargin: "0px 0px -30px 0px" });

document.querySelectorAll(".reveal").forEach((el, index) => {
  el.style.transitionDelay = `${Math.min((index % 4) * 70, 210)}ms`;
  observer.observe(el);
});

if (window.matchMedia("(pointer:fine)").matches && glow) {
  window.addEventListener("pointermove", event => {
    glow.style.left = `${event.clientX}px`;
    glow.style.top = `${event.clientY}px`;
  }, { passive: true });
}

const pageUrl = encodeURIComponent(window.location.href);
const message = encodeURIComponent(
  "Здравствуйте! Хочу обсудить работы на элеваторе. Могу отправить описание, фото и видео объекта."
);

const whatsapp = document.getElementById("whatsappLink");
const telegram = document.getElementById("telegramLink");
const email = document.getElementById("emailLink");

whatsapp.href = `https://api.whatsapp.com/send?text=${message}`;
telegram.href = `https://t.me/share/url?url=${pageUrl}&text=${message}`;
email.href = `mailto:?subject=${encodeURIComponent("Запрос — Проект Элеватор")}&body=${message}`;

document.getElementById("year").textContent = new Date().getFullYear();

document.querySelectorAll('a[href^="#"]').forEach(link => {
  link.addEventListener("click", event => {
    const id = link.getAttribute("href");
    if (!id || id === "#") return;
    const target = document.querySelector(id);
    if (!target) return;
    event.preventDefault();
    const offset = 74;
    const top = target.getBoundingClientRect().top + window.scrollY - offset;
    window.scrollTo({ top, behavior: "smooth" });
  });
});