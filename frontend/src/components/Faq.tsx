import { motion } from "motion/react";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";

const faqItems = [
  {
    question: "What is WastePilot, and who is it for?",
    answer:
      "WastePilot is an internal circular intelligence platform for SMEs. It helps teams reduce material waste, improve reuse decisions, and track circular performance without adding heavy manual workflows.",
  },
  {
    question: "How does invoice photo scanning work?",
    answer:
      "You upload or photograph an invoice, then AI Vision extracts structured fields such as material name, quantity, and price. Before saving, your team can review and confirm the extracted values.",
  },
  {
    question: "Can we still operate if AI services are unavailable?",
    answer:
      "Yes. WastePilot is designed with a manual-entry fallback. Your production and inventory logging continue to work even if AI OCR or insight services are temporarily down.",
  },
  {
    question: "How is circularity score calculated?",
    answer:
      "The dashboard uses: (Material Reused + Material Recycled) / Total Material Input x 100. This gives a clear percentage that tracks how effectively materials stay in your internal loop.",
  },
  {
    question: "What types of recommendations does the AI provide?",
    answer:
      "Recommendations are short, actionable suggestions, for example repurposing leftover material into a specific secondary product or adjusting process settings when waste anomalies are detected.",
  },
  {
    question: "Does WastePilot support multiple warehouses in this version?",
    answer:
      "The current MVP is focused on a single production site to keep operations simple and reliable. Multi-warehouse workflows are planned for later phases.",
  },
];

const Faq = () => {
  return (
    <section id="faq" className="py-24 px-6 md:px-16 lg:px-24">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-12">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="liquid-glass rounded-full px-3.5 py-1 text-xs font-medium text-white font-body inline-block mb-4"
          >
            FAQ
          </motion.div>
          <motion.h2
            initial={{ opacity: 0, y: 30, filter: "blur(8px)" }}
            whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            viewport={{ once: true }}
            transition={{ duration: 0.6 }}
            className="text-4xl md:text-5xl lg:text-6xl font-heading italic text-white tracking-tight leading-[0.9]"
          >
            Answers for circular teams.
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.15 }}
            className="text-white/60 font-body font-light text-sm md:text-base max-w-2xl mx-auto mt-6"
          >
            Everything you need to know about how WastePilot supports internal resource optimization.
          </motion.p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.5 }}
          className="liquid-glass rounded-3xl p-4 md:p-6"
        >
          <Accordion type="single" collapsible className="w-full">
            {faqItems.map((item, i) => (
              <AccordionItem key={item.question} value={`item-${i}`} className="border-white/10">
                <AccordionTrigger className="text-left text-white font-body text-base md:text-lg py-5 hover:no-underline">
                  {item.question}
                </AccordionTrigger>
                <AccordionContent className="text-white/65 font-body font-light text-sm md:text-base leading-relaxed pr-8">
                  {item.answer}
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </motion.div>
      </div>
    </section>
  );
};

export default Faq;
