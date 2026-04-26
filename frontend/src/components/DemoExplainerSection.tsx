import { motion } from "motion/react";
import { PlayCircle } from "lucide-react";
import HlsVideo from "@/components/HlsVideo";

const DemoExplainerSection = () => {
  return (
    <section id="demo-explainer" className="relative min-h-[760px] py-32 px-6 md:px-16 lg:px-24 overflow-hidden">
      <HlsVideo
        src="https://stream.mux.com/NcU3HlHeF7CUL86azTTzpy3Tlb00d6iF3BmCdFslMJYM.m3u8"
        className="absolute inset-0 w-full h-full object-cover z-0"
        style={{ filter: "saturate(0)" }}
      />

      <div className="absolute inset-0 bg-black/45 z-[1]" />
      <div className="absolute top-0 left-0 right-0 z-[2] h-[200px]" style={{ background: "linear-gradient(to bottom, black, transparent)" }} />
      <div className="absolute bottom-0 left-0 right-0 z-[2] h-[200px]" style={{ background: "linear-gradient(to top, black, transparent)" }} />

      <div className="relative z-10 max-w-6xl mx-auto text-center">
        

        <motion.h2
          initial={{ opacity: 0, y: 30, filter: "blur(10px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9]"
        >
          Product Demo Placeholder
        </motion.h2>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.25 }}
          className="text-white/60 font-body font-light text-sm md:text-base max-w-2xl mx-auto mt-6"
        >
          This section is reserved for your SaaS-style explainer demo.
          Replace this placeholder with your final ad/demo narrative and interactive playback flow.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.35 }}
          className="liquid-glass-strong rounded-3xl p-4 md:p-6 mt-10"
        >
          <div className="rounded-2xl border border-dashed border-white/30 bg-black/30 min-h-[300px] md:min-h-[420px] flex flex-col items-center justify-center gap-3">
            <PlayCircle className="w-10 h-10 text-white/70" />
            <p className="text-white font-body font-medium">Demo video and storyboard area</p>
            <p className="text-white/50 font-body text-sm">Placeholder ready for final SaaS explainer embed</p>
          </div>
        </motion.div>
      </div>
    </section>
  );
};

export default DemoExplainerSection;
