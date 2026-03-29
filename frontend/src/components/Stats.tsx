import { motion, useScroll, useTransform } from "motion/react";
import { useRef } from "react";
import HlsVideo from "./HlsVideo";

const stats = [
  { value: "95%", label: "Waste reduction" },
  { value: "3s", label: "AI response time" },
  { value: "500+", label: "SMEs onboarded" },
  { value: "78%", label: "Avg circularity score" },
];

const Stats = () => {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const scale = useTransform(scrollYProgress, [0, 0.5], [0.9, 1]);

  return (
    <section ref={ref} id="stats" className="relative py-32 px-6 md:px-16 lg:px-24 overflow-hidden">
      <HlsVideo
        src="https://stream.mux.com/NcU3HlHeF7CUL86azTTzpy3Tlb00d6iF3BmCdFslMJYM.m3u8"
        className="absolute inset-0 w-full h-full object-cover z-0"
        style={{ filter: "saturate(0)" }}
      />
      <div className="absolute top-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to bottom, black, transparent)" }} />
      <div className="absolute bottom-0 left-0 right-0 z-[1] h-[200px]" style={{ background: "linear-gradient(to top, black, transparent)" }} />

      <motion.div style={{ scale }} className="relative z-10 max-w-5xl mx-auto">
        <div className="liquid-glass rounded-3xl p-12 md:p-16">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8 text-center">
            {stats.map((s, i) => (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: i * 0.1 }}
              >
                <div className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white">{s.value}</div>
                <div className="text-white/60 font-body font-light text-sm mt-2">{s.label}</div>
              </motion.div>
            ))}
          </div>
        </div>
      </motion.div>
    </section>
  );
};

export default Stats;
