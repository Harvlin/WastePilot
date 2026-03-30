import { motion } from "motion/react";

const solutionFeatures = [
  {
    title: "Smart Capture",
    description:
      "Capture invoice and material data with OCR, then review and confirm records in seconds.",
  },
  {
    title: "Operations Hub",
    description:
      "Run batches, inventory movement, and waste logging from one connected workspace.",
  },
  {
    title: "Actionable Insights",
    description:
      "Receive anomaly alerts and practical recommendations to improve reuse and reduce loss.",
  },
  {
    title: "Performance Reporting",
    description:
      "Track circular KPIs and share progress clearly with operations and sustainability teams.",
  },
];

const SolutionSection = () => {
  return (
    <section id="solution" className="relative z-20 isolate bg-black py-32 md:py-44 px-6 md:px-16 lg:px-24">
      <div className="max-w-6xl mx-auto">
        <motion.p
          initial={{ opacity: 0, y: 14 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="text-xs tracking-[3px] uppercase text-[hsl(var(--palette-light-green))]"
        >
          Solution
        </motion.p>

        <motion.h2
          initial={{ opacity: 0, y: 24, filter: "blur(8px)" }}
          whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="mt-5 text-4xl md:text-6xl text-white leading-[1.05]"
        >
          The platform for <span className="font-heading italic text-[hsl(var(--palette-tea-green))]">circular</span> operations
        </motion.h2>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.15 }}
          className="mt-10 rounded-2xl overflow-hidden"
        >
          <video
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260325_125119_8e5ae31c-0021-4396-bc08-f7aebeb877a2.mp4"
            className="w-full aspect-[3/1] object-cover"
            autoPlay
            loop
            muted
            playsInline
          />
        </motion.div>

        <div className="mt-12 grid grid-cols-1 md:grid-cols-4 gap-8">
          {solutionFeatures.map((feature, index) => (
            <motion.div
              key={feature.title}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: 0.06 * index }}
            >
              <h3 className="font-semibold text-base text-white">{feature.title}</h3>
              <p className="mt-2 text-muted-foreground text-sm leading-relaxed">{feature.description}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default SolutionSection;