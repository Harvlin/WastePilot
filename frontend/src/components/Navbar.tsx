import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import { ArrowUpRight, Menu, X } from "lucide-react";
import { Link } from "react-router-dom";
import { isMockAuthenticated } from "@/lib/mock-auth";

const navLinks = [
  { label: "Home", id: "home" },
  { label: "How It Works", id: "how-it-works" },
  { label: "Features", id: "features" },
  { label: "Stats", id: "stats" },
  { label: "FAQ", id: "faq" },
];

const Navbar = () => {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [hoveredLink, setHoveredLink] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState<string>("home");
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const scrollToSection = (sectionId: string) => {
    const section = document.getElementById(sectionId);
    if (!section) return;

    const topOffset = 100;
    const sectionTop = section.getBoundingClientRect().top + window.scrollY - topOffset;
    window.history.replaceState(null, "", `#${sectionId}`);
    window.scrollTo({ top: sectionTop, behavior: "smooth" });
  };

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 30);

      const topOffset = 130;
      let current = navLinks[0].id;

      navLinks.forEach((link) => {
        const section = document.getElementById(link.id);
        if (section && section.offsetTop - topOffset <= window.scrollY) {
          current = link.id;
        }
      });

      setActiveSection(current);
    };

    window.addEventListener("scroll", handleScroll);
    handleScroll();

    if (window.location.hash) {
      const hashId = window.location.hash.replace("#", "");
      setTimeout(() => scrollToSection(hashId), 50);
    }

    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    setIsAuthenticated(isMockAuthenticated());

    const syncAuth = () => setIsAuthenticated(isMockAuthenticated());
    window.addEventListener("storage", syncAuth);

    return () => window.removeEventListener("storage", syncAuth);
  }, []);

  const ctaPath = isAuthenticated ? "/dashboard" : "/auth";
  const ctaLabel = isAuthenticated ? "Open Workspace" : "Get Started";

  return (
    <>
      <motion.nav
        initial={{ y: -40, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.6, ease: "easeOut" }}
        className="fixed top-4 left-0 right-0 z-50 flex items-center justify-between px-6 md:px-12"
      >
        {/* Logo */}
        <Link to="/" className="w-[160px] h-12 flex items-center">
          <motion.div
            whileHover={{ scale: 1.1, rotate: -5 }}
            whileTap={{ scale: 0.95 }}
            className="w-full h-full flex items-center cursor-pointer"
          >
            <img
              src="/logoDevSpeak.png"
              alt="WastePilot"
              className="h-full w-auto object-contain"
            />
          </motion.div>
        </Link>

        {/* Center nav pill */}
        <motion.div
          animate={{
            backdropFilter: scrolled ? "blur(20px)" : "blur(4px)",
            background: scrolled ? "rgba(255,255,255,0.04)" : "rgba(255,255,255,0.01)",
          }}
          transition={{ duration: 0.3 }}
          className="hidden md:flex items-center gap-1 liquid-glass rounded-full px-2 py-1.5 relative"
        >
          {navLinks.map((link) => (
            <button
              key={link.id}
              type="button"
              className="relative text-sm font-medium text-white/90 font-body px-4 py-2 rounded-full transition-colors"
              onMouseEnter={() => setHoveredLink(link.id)}
              onMouseLeave={() => setHoveredLink(null)}
              onClick={() => scrollToSection(link.id)}
            >
              {activeSection === link.id && (
                <motion.span
                  layoutId="nav-active"
                  className="absolute inset-0 bg-white/15 rounded-full"
                  transition={{ type: "spring", stiffness: 420, damping: 35 }}
                />
              )}
              {hoveredLink === link.id && activeSection !== link.id && (
                <motion.span
                  layoutId="nav-hover"
                  className="absolute inset-0 bg-white/10 rounded-full"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.2 }}
                />
              )}
              <span className="relative z-10">{link.label}</span>
            </button>
          ))}
          <Link to={ctaPath}>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="bg-white text-black font-body font-medium text-sm px-5 py-2 rounded-full flex items-center gap-1.5 hover:bg-white/90 transition-colors ml-1"
            >
              {ctaLabel} <ArrowUpRight className="w-3.5 h-3.5" />
            </motion.button>
          </Link>
        </motion.div>

        {/* Mobile menu button */}
        <motion.button
          whileTap={{ scale: 0.9 }}
          className="md:hidden w-12 h-12 rounded-full liquid-glass flex items-center justify-center"
          onClick={() => setMobileOpen(!mobileOpen)}
        >
          {mobileOpen ? <X className="w-5 h-5 text-white" /> : <Menu className="w-5 h-5 text-white" />}
        </motion.button>

        {/* Spacer for balance (desktop) */}
        <div className="w-[160px] h-12 hidden md:block" />
      </motion.nav>

      {/* Mobile menu */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0, y: -20, filter: "blur(10px)" }}
            animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            exit={{ opacity: 0, y: -20, filter: "blur(10px)" }}
            transition={{ duration: 0.3 }}
            className="fixed top-20 left-4 right-4 z-50 liquid-glass-strong rounded-2xl p-6 md:hidden"
          >
            <div className="flex flex-col gap-2">
              {navLinks.map((link, i) => (
                <motion.button
                  key={link.id}
                  type="button"
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.05 }}
                  className={`text-left text-white/90 font-body font-medium text-lg py-3 px-4 rounded-xl transition-colors ${
                    activeSection === link.id ? "bg-white/10" : "hover:bg-white/5"
                  }`}
                  onClick={() => {
                    scrollToSection(link.id);
                    setMobileOpen(false);
                  }}
                >
                  {link.label}
                </motion.button>
              ))}
              <Link to={ctaPath} onClick={() => setMobileOpen(false)}>
                <motion.button
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: navLinks.length * 0.05 }}
                  className="w-full bg-white text-black font-body font-medium text-sm py-3 rounded-full flex items-center justify-center gap-2 mt-2"
                >
                  {ctaLabel} <ArrowUpRight className="w-3.5 h-3.5" />
                </motion.button>
              </Link>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};

export default Navbar;
