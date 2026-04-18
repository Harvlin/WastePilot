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
          Close each batch with confidence.
        </motion.h2>
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.2 }}
          className="text-white/60 font-body font-light text-sm md:text-base max-w-lg mt-6"
        >
          Move from manual closing to review-and-confirm flows with batch summaries,
          audit-ready logs, and clearer landfill-aware scoring.
        </motion.p>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.4 }}
          className="flex flex-wrap items-center justify-center gap-4 mt-8"
        >
          <Link to={ctaPath}>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="liquid-glass-strong rounded-full px-6 py-3 font-body font-medium text-sm text-[hsl(var(--palette-light-green))] hover:bg-[hsl(var(--palette-house-green))]/35 transition-colors"
            >
              Open Workspace
            </motion.button>
          </Link>
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={handleHowItWorks}
            className="bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] font-body font-medium text-sm px-6 py-3 rounded-full hover:bg-[hsl(var(--palette-light-green))] transition-colors"
          >
            View 3-Step Flow
          </motion.button>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.5 }}
          className="mt-5 flex flex-wrap items-center justify-center gap-2"
        >
          {[
            "Batch Close Assistant",
            "Activity Logs + Audit Trail",
            "Confidence and Red Flags",
            "Landfill-Aware Score",
          ].map((item) => (
            <span
              key={item}
              className="liquid-glass rounded-full px-3 py-1 text-xs font-body text-[hsl(var(--palette-light-green))]/90"
            >
              {item}
            </span>
          ))}
        </motion.div>

        <div className="mt-32 pt-8 border-t border-[hsl(var(--palette-house-green))]/50 w-full flex flex-col md:flex-row items-center justify-between gap-4">
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
