import { motion } from "motion/react";
import { ScanLine, Recycle, BarChart3, BellRing } from "lucide-react";

const features = [
  {
    icon: ScanLine,
    title: "AI Vision OCR",
    description: "Photograph invoices, get structured data. Zero typing required.",
  },
  {
    icon: Recycle,
    title: "Circular Insights",
    description: "AI recommends how to repurpose waste — turning scraps into new products.",
  },
  {
    icon: BarChart3,
    title: "Circularity Dashboard",
    description: "Real-time scores, waste breakdowns, and environmental impact metrics.",
  },
  {
    icon: BellRing,
    title: "Anomaly Alerts",
    description: "Z-score detection spots machinery issues before they become costly.",
  },
];

const FeaturesGrid = () => {
  return (
    <section className="py-24 px-6 md:px-16 lg:px-24">
      <div className="text-center mb-16">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="liquid-glass rounded-full px-3.5 py-1 text-xs font-medium text-white font-body inline-block mb-4"
        >
          Why Waste Pilot
        </motion.div>
        <motion.h2
          initial={{ opacity: 0, y: 30, filter: "blur(8px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9]"
        >
          Zero-waste intelligence.
        </motion.h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 max-w-6xl mx-auto">
        {features.map((f, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: i * 0.1 }}
            whileHover={{ y: -8, transition: { duration: 0.3 } }}
            className="liquid-glass rounded-2xl p-6 space-y-4 cursor-default"
          >
            <motion.div
              whileHover={{ rotate: 10, scale: 1.1 }}
              className="liquid-glass-strong rounded-full w-10 h-10 flex items-center justify-center"
            >
              <f.icon className="w-4 h-4 text-white" />
            </motion.div>
            <h3 className="text-lg font-heading italic text-white">{f.title}</h3>
            <p className="text-white/60 font-body font-light text-sm">{f.description}</p>
          </motion.div>
        ))}
      </div>
    </section>
  );
};

export default FeaturesGrid;
