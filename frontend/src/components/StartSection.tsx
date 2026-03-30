import { motion, useScroll, useTransform } from "motion/react";
import { ArrowUpRight } from "lucide-react";
import { useRef } from "react";
import { Link } from "react-router-dom";
import HlsVideo from "./HlsVideo";
import { isMockAuthenticated } from "@/lib/mock-auth";

const StartSection = () => {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const y = useTransform(scrollYProgress, [0, 1], [60, -60]);
  const scale = useTransform(scrollYProgress, [0, 0.5], [0.95, 1]);
  const ctaPath = isMockAuthenticated() ? "/dashboard" : "/auth";

  return (
    <section
      ref={ref}
      id="how-it-works"
      className="relative min-h-[700px] py-28 md:py-32 px-6 md:px-16 lg:px-24 overflow-hidden"
    >
      <HlsVideo
        src="https://stream.mux.com/9JXDljEVWYwWu01PUkAemafDugK89o01BR6zqJ3aS9u00A.m3u8"
        className="absolute inset-0 w-full h-full object-cover z-0"
      />
      <div className="absolute top-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to bottom, black, transparent)" }} />
      <div className="absolute bottom-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to top, black, transparent)" }} />

      <motion.div style={{ y, scale }} className="relative z-10 flex flex-col items-center justify-center text-center min-h-[500px]">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="liquid-glass rounded-full px-3.5 py-1 text-xs font-medium text-[hsl(var(--palette-light-green))] font-body inline-block mb-4"
        >
          How It Works
        </motion.div>
        <motion.h2
          initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9] max-w-3xl"
        >
          Snap. Track. Optimize.
        </motion.h2>
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.3 }}
          className="text-white/60 font-body font-light text-sm md:text-base max-w-xl mt-6"
        >
          Take a photo of your invoice. Our AI extracts data instantly, logs materials,
          and starts tracking your circular economy metrics — all in seconds.
        </motion.p>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.5 }}
        >
          <Link to={ctaPath}>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="liquid-glass-strong rounded-full px-6 py-3 font-body font-medium text-sm text-white flex items-center gap-2 mt-8 hover:bg-white/10 transition-colors"
            >
              Get Started <ArrowUpRight className="w-4 h-4" />
            </motion.button>
          </Link>
        </motion.div>
      </motion.div>
    </section>
  );
};

export default StartSection;
