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
    question: "How does end-of-batch work now?",
    answer:
      "WastePilot uses a Batch Close Assistant. The system auto-builds a close summary from your operational logs, then operators review and confirm instead of re-entering everything from scratch. A reason is only required when variance is above threshold.",
  },
  {
    question: "How is circularity score calculated?",
    answer:
      "The score combines recovery, waste efficiency, and landfill avoidance. Landfill has stronger weight, a landfill cap is applied when share is high, and the result is adjusted by confidence and integrity penalties so weak data cannot inflate score.",
  },
  {
    question: "How do we keep data trustworthy without IoT sensors?",
    answer:
      "WastePilot includes Activity Logs, Audit Trail, confidence scoring, and red-flag detection. The system records who changed what and when, and post-score edits are explicitly marked for reviewer attention.",
  },
  {
    question: "Can we still operate if AI services are unavailable?",
    answer:
      "Yes. WastePilot is designed with manual fallback paths. Production, inventory, and waste logging continue to work even if OCR or insight services are temporarily unavailable.",
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
            className="text-[hsl(var(--palette-light-green))] opacity-80 font-body font-light text-sm md:text-base max-w-2xl mx-auto mt-6"
          >
            Everything you need to know about trusted scoring, low-friction operations, and circular performance.
          </motion.p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.5 }}
          className="liquid-glass rounded-3xl p-4 md:p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]"
        >
          <Accordion type="single" collapsible className="w-full">
            {faqItems.map((item, i) => (
              <AccordionItem key={item.question} value={`item-${i}`} className="border-[hsl(var(--palette-house-green))]/70">
                <AccordionTrigger className="text-left text-[hsl(var(--palette-light-green))] font-body text-base md:text-lg py-5 hover:no-underline hover:text-[hsl(var(--palette-tea-green))]">
                  {item.question}
                </AccordionTrigger>
                <AccordionContent className="text-[hsl(var(--palette-light-green))] opacity-80 font-body font-light text-sm md:text-base leading-relaxed pr-8">
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
