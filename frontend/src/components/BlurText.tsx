import { useRef, useEffect, useState } from "react";
import { motion, useAnimation } from "motion/react";

interface BlurTextProps {
  text: string;
  className?: string;
  delay?: number;
}

const BlurText = ({ text, className = "", delay = 0 }: BlurTextProps) => {
  const words = text.split(" ");
  const containerRef = useRef<HTMLDivElement>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) setInView(true);
      },
      { threshold: 0.3 }
    );
    if (containerRef.current) observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={containerRef} className={className}>
      {words.map((word, i) => (
        <motion.span
          key={i}
          className="inline-block mr-[0.25em]"
          initial={{ filter: "blur(10px)", opacity: 0, y: 50 }}
          animate={
            inView
              ? { filter: "blur(0px)", opacity: 1, y: 0 }
              : {}
          }
          transition={{
            duration: 0.35,
            delay: delay + i * 0.1,
            ease: "easeOut",
          }}
        >
          {word}
        </motion.span>
      ))}
    </div>
  );
};

export default BlurText;
