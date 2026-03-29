import { motion } from "motion/react";
import { ArrowUpRight } from "lucide-react";
import { toast } from "sonner";

const rows = [
  {
    title: "AI Vision that reads your invoices.",
    description:
      "Snap a photo. Our OCR engine extracts material names, quantities, and prices — then auto-populates your inventory. No more manual data entry.",
    button: "Learn more",
    imageUrl:
      "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExcDV2OHRvNnJiYm91d3NuYnRpZ3Q0OXhiOGRhMGthbGFleDh4aHRteCZlcD12MV9naWZzX3NlYXJjaCZjdD1n/3o7btNa0RUYa5E7iiQ/giphy.gif",
    reverse: false,
  },
  {
    title: "Anomaly detection that prevents waste.",
    description:
      "Waste Pilot monitors every production batch. When waste spikes beyond normal thresholds, AI alerts you instantly — before it costs you more.",
    button: "See how it works",
    imageUrl:
      "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExMmJtdXczd2V0NWx4M3RieDdtZG04cTl5cHJ6dmo2NXlrb21qcGlzYSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/l0HlNQ03J5JxX6lva/giphy.gif",
    reverse: true,
  },
];

const FeaturesChess = () => {
  const handleFeatureAction = (title: string) => {
    const faq = document.getElementById("faq");
    if (faq) {
      const topOffset = 100;
      const sectionTop = faq.getBoundingClientRect().top + window.scrollY - topOffset;
      window.scrollTo({ top: sectionTop, behavior: "smooth" });
    }

    toast.info(`${title} details are mocked in FAQ and internal workspace flows.`);
  };

  return (
    <section id="features" className="py-24 px-6 md:px-16 lg:px-24">
      <div className="text-center mb-16">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="liquid-glass rounded-full px-3.5 py-1 text-xs font-medium text-white font-body inline-block mb-4"
        >
          Core Capabilities
        </motion.div>
        <motion.h2
          initial={{ opacity: 0, y: 30, filter: "blur(8px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9]"
        >
          Intelligence meets sustainability.
        </motion.h2>
      </div>

      <div className="space-y-24 max-w-6xl mx-auto">
        {rows.map((row, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 60 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }}
            transition={{ duration: 0.7, ease: "easeOut" }}
            className={`flex flex-col lg:flex-row gap-12 items-center ${row.reverse ? "lg:flex-row-reverse" : ""}`}
          >
            <div className="flex-1 space-y-4">
              <h3 className="text-2xl md:text-3xl font-heading italic text-white tracking-tight">
                {row.title}
              </h3>
              <p className="text-white/60 font-body font-light text-sm max-w-md">
                {row.description}
              </p>
              <motion.button
                whileHover={{ scale: 1.05, x: 5 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => handleFeatureAction(row.title)}
                className="liquid-glass-strong rounded-full px-5 py-2.5 font-body font-medium text-sm text-white flex items-center gap-2 hover:bg-white/10 transition-colors mt-2"
              >
                {row.button} <ArrowUpRight className="w-3.5 h-3.5" />
              </motion.button>
            </div>

            <motion.div
              whileHover={{ scale: 1.02 }}
              transition={{ type: "spring", stiffness: 300 }}
              className="flex-1 liquid-glass rounded-2xl overflow-hidden aspect-video"
            >
              <img src={row.imageUrl} alt={row.title} className="w-full h-full object-cover" />
            </motion.div>
          </motion.div>
        ))}
      </div>
    </section>
  );
};

export default FeaturesChess;
