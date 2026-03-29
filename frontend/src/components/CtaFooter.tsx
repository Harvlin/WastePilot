import { motion } from "motion/react";
import { Link } from "react-router-dom";
import HlsVideo from "./HlsVideo";
import { isMockAuthenticated } from "@/lib/mock-auth";
import { toast } from "sonner";

const CtaFooter = () => {
  const ctaPath = isMockAuthenticated() ? "/dashboard" : "/auth";

  const handleHowItWorks = () => {
    const section = document.getElementById("how-it-works");
    if (section) {
      const topOffset = 100;
      const sectionTop = section.getBoundingClientRect().top + window.scrollY - topOffset;
      window.scrollTo({ top: sectionTop, behavior: "smooth" });
    }
    toast.info("How it works section is ready above.");
  };

  const handleFooterLink = (label: string) => {
    toast.info(`${label} page is mocked and ready for API-backed content.`);
  };

  return (
    <section id="get-started" className="relative py-32 px-6 md:px-16 lg:px-24 overflow-hidden">
      <HlsVideo
        src="https://stream.mux.com/8wrHPCX2dC3msyYU9ObwqNdm00u3ViXvOSHUMRYSEe5Q.m3u8"
        className="absolute inset-0 w-full h-full object-cover z-0"
      />
      <div className="absolute top-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to bottom, black, transparent)" }} />
      <div className="absolute bottom-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to top, black, transparent)" }} />

      <div className="relative z-10 flex flex-col items-center text-center">
        <motion.h2
          initial={{ opacity: 0, y: 40, filter: "blur(10px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.7 }}
          className="text-5xl md:text-6xl lg:text-7xl font-heading italic text-white tracking-tight leading-[0.9] max-w-3xl"
        >
          Close the loop. Start today.
        </motion.h2>
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.2 }}
          className="text-white/60 font-body font-light text-sm md:text-base max-w-lg mt-6"
        >
          WastePilot is free to use. Start improving circular operations with
          AI-powered insights, anomaly alerts, and smarter material decisions.
        </motion.p>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.4 }}
          className="flex items-center gap-4 mt-8"
        >
          <Link to={ctaPath}>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="liquid-glass-strong rounded-full px-6 py-3 font-body font-medium text-sm text-white hover:bg-white/10 transition-colors"
            >
              Access Free Workspace
            </motion.button>
          </Link>
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={handleHowItWorks}
            className="bg-white text-black font-body font-medium text-sm px-6 py-3 rounded-full hover:bg-white/90 transition-colors"
          >
            See How It Works
          </motion.button>
        </motion.div>

        <div className="mt-32 pt-8 border-t border-white/10 w-full flex flex-col md:flex-row items-center justify-between gap-4">
          <span className="text-white/40 font-body text-xs">© 2026 Waste Pilot</span>
          <div className="flex items-center gap-6">
            {["Privacy", "Terms", "Contact"].map((link) => (
              <button
                key={link}
                type="button"
                onClick={() => handleFooterLink(link)}
                className="text-white/40 font-body text-xs hover:text-white/60 transition-colors"
              >
                {link}
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
};

export default CtaFooter;
