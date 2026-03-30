import { MotionValue, motion, useScroll, useTransform } from "motion/react";
import { useMemo, useRef } from "react";

interface RevealWordProps {
  word: string;
  index: number;
  total: number;
  progress: MotionValue<number>;
  isHighlighted?: boolean;
  baseColorClass?: string;
}

const normalizeWord = (word: string) => word.toLowerCase().replace(/[^a-z]/g, "");

const RevealWord = ({
  word,
  index,
  total,
  progress,
  isHighlighted = false,
  baseColorClass = "text-[hsl(var(--hero-subtitle))]",
}: RevealWordProps) => {
  const safeTotal = Math.max(total, 1);
  const start = (index / safeTotal) * 0.74;
  const end = Math.min(start + 0.16, 1);
  const opacity = useTransform(progress, [start, end], [0.15, 1]);

  return (
    <motion.span
      style={{ opacity }}
      className={`inline-block ${isHighlighted ? "text-foreground" : baseColorClass}`}
    >
      {word}&nbsp;
    </motion.span>
  );
};

const paragraphOne =
  "We're building a workspace where material data meets operational clarity - where teams spot waste faster, recover value sooner, and turn every production cycle into measurable circular progress.";

const paragraphTwo =
  "A platform where scanning, operations, and insights flow together - with less manual entry, fewer blind spots, and more reuse-focused decisions for every team.";

const highlightWords = new Set(["material", "data", "clarity"]);

const MissionSection = () => {
  const sectionRef = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start 80%", "end 35%"],
  });

  const paragraphOneWords = useMemo(() => paragraphOne.split(" "), []);
  const paragraphTwoWords = useMemo(() => paragraphTwo.split(" "), []);

  return (
    <section
      ref={sectionRef}
      id="mission"
      className="relative pt-2 md:pt-4 pb-32 md:pb-44 px-6 md:px-16 lg:px-24"
    >
      <div className="max-w-6xl mx-auto">
        <div className="mx-auto w-[min(90vw,800px)] h-[min(90vw,800px)] rounded-3xl overflow-hidden">
          <video
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260325_132944_a0d124bb-eaa1-4082-aa30-2310efb42b4b.mp4"
            className="w-full h-full object-cover"
            autoPlay
            loop
            muted
            playsInline
          />
        </div>

        <div className="mt-16 max-w-5xl mx-auto">
          <p className="text-2xl md:text-4xl lg:text-5xl font-medium tracking-[-1px] leading-[1.2]">
            {paragraphOneWords.map((word, index) => (
              <RevealWord
                key={`mission-p1-${index}-${word}`}
                word={word}
                index={index}
                total={paragraphOneWords.length}
                progress={scrollYProgress}
                isHighlighted={highlightWords.has(normalizeWord(word))}
              />
            ))}
          </p>

          <p className="text-xl md:text-2xl lg:text-3xl font-medium mt-10 leading-[1.35]">
            {paragraphTwoWords.map((word, index) => (
              <RevealWord
                key={`mission-p2-${index}-${word}`}
                word={word}
                index={index}
                total={paragraphTwoWords.length}
                progress={scrollYProgress}
                baseColorClass="text-muted-foreground"
              />
            ))}
          </p>
        </div>
      </div>
    </section>
  );
};

export default MissionSection;