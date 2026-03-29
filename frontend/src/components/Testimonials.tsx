import { motion } from "motion/react";

const testimonials = [
  {
    quote:
      "Waste Pilot cut our material waste by 60% in the first month. The AI insights are like having a sustainability consultant on staff 24/7.",
    name: "Rina Hartono",
    role: "Operations Manager, EcoTextile Co.",
  },
  {
    quote:
      "The OCR feature alone saved us 15 hours a week of manual data entry. Now we actually have time to act on the circular recommendations.",
    name: "David Kusuma",
    role: "Founder, GreenCraft Studios",
  },
  {
    quote:
      "We caught a machine malfunction through anomaly detection before it cost us a full batch. WastePilot improved our throughput in week one.",
    name: "Siti Amara",
    role: "Production Lead, ReLoop Manufacturing",
  },
];

const Testimonials = () => {
  return (
    <section className="py-24 px-6 md:px-16 lg:px-24">
      <div className="text-center mb-16">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="liquid-glass rounded-full px-3.5 py-1 text-xs font-medium text-white font-body inline-block mb-4"
        >
          Success Stories
        </motion.div>
        <motion.h2
          initial={{ opacity: 0, y: 30, filter: "blur(8px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9]"
        >
          Real results. Real impact.
        </motion.h2>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto">
        {testimonials.map((t, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: i * 0.15 }}
            whileHover={{ y: -6, transition: { duration: 0.3 } }}
            className="liquid-glass rounded-2xl p-8 flex flex-col justify-between"
          >
            <p className="text-white/80 font-body font-light text-sm italic mb-6">"{t.quote}"</p>
            <div>
              <div className="text-white font-body font-medium text-sm">{t.name}</div>
              <div className="text-white/50 font-body font-light text-xs">{t.role}</div>
            </div>
          </motion.div>
        ))}
      </div>
    </section>
  );
};

export default Testimonials;
