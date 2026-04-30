import { motion, useScroll, useTransform } from "motion/react";
import { ArrowUpRight, Play } from "lucide-react";
import { useRef, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import BlurText from "./BlurText";
import { useAuth } from "@/features/auth/auth-context";
import { toast } from "sonner";

const techStack = ["Spring Boot", "Next.js 15", "Gemini Flash", "Docker", "MySQL", "Oauth2"];

const Hero = () => {
  const sectionRef = useRef<HTMLElement>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [videoLoaded, setVideoLoaded] = useState(false);
  const { isAuthenticated } = useAuth();
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end start"],
  });

  const videoScale = useTransform(scrollYProgress, [0, 1], [1, 1.2]);
  const videoOpacity = useTransform(scrollYProgress, [0, 0.8], [1, 0]);
  const contentY = useTransform(scrollYProgress, [0, 0.5], [0, -80]);
  const ctaPath = isAuthenticated ? "/dashboard" : "/auth";

  const handleWatchDemo = () => {
    const section = document.getElementById("how-it-works");
    if (section) {
      const topOffset = 100;
      const sectionTop = section.getBoundingClientRect().top + window.scrollY - topOffset;
      window.scrollTo({ top: sectionTop, behavior: "smooth" });
    }
    toast.info("Demo section ready below.");
  };

  useEffect(() => {
    const section = sectionRef.current;
    if (!section) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && !videoLoaded) {
            setVideoLoaded(true);
          }
        });
      },
      { root: null, rootMargin: "200px", threshold: 0.1 }
    );

    observer.observe(section);
    return () => observer.disconnect();
  }, [videoLoaded]);

  useEffect(() => {
    if (!videoLoaded) return;
    const videoEl = document.querySelector('#hero-lazy-video') as HTMLVideoElement | null;
    if (!videoEl) return;
    // set src when visible so network fetch happens later
    const src = "https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260307_083826_e938b29f-a43a-41ec-a153-3d4730578ab8.mp4";
    if (!videoEl.src) {
      videoEl.src = src;
      // try to play (muted allows autoplay in most browsers)
      videoEl
        .play()
        .catch(() => {
          /* ignore autoplay rejection */
        });
    }
  }, [videoLoaded]);

  return (
    <section ref={sectionRef} id="home" className="relative overflow-visible h-[1000px] bg-black">
      {/* Background video with parallax (lazy-loaded source) */}
      <motion.video
        id="hero-lazy-video"
        ref={videoRef}
        style={{ scale: videoScale, opacity: videoOpacity }}
        className="absolute top-[20%] w-full h-auto object-contain z-0"
        autoPlay
        loop
        muted
        playsInline
        poster="/images/hero_bg.jpeg"
        preload="metadata"
      />

      <div className="absolute inset-0 bg-black/5 z-0" />
      <div
        className="absolute bottom-0 left-0 right-0 z-[1] h-[300px]"
        style={{ background: "linear-gradient(to bottom, transparent, black)" }}
      />

      {/* Content with scroll parallax */}
      <motion.div
        style={{ y: contentY }}
        className="relative z-10 flex flex-col items-center text-center pt-[180px] md:pt-[200px] h-full px-6"
      >
        {/* Heading */}
        <BlurText
          text="Smart Circular Economy for Your Business"
          className="text-5xl md:text-7xl lg:text-[5.5rem] font-heading italic text-white leading-[0.8] tracking-[-4px] max-w-5xl"
        />

        {/* Subtext */}
        <motion.p
          initial={{ opacity: 0, filter: "blur(10px)" }}
          animate={{ opacity: 1, filter: "blur(0px)" }}
          transition={{ duration: 0.6, delay: 0.8 }}
          className="text-white/60 font-body font-light text-sm md:text-base max-w-xl mt-8"
        >
          Transform waste into opportunity. Waste Pilot uses AI to optimize materials,
          predict anomalies, and maximize your circularity score — all automatically.
        </motion.p>

        {/* CTA */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 1.1 }}
          className="flex items-center gap-4 mt-8"
        >
          <Link to={ctaPath}>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="liquid-glass-strong rounded-full px-6 py-3 font-body font-medium text-sm text-white flex items-center gap-2 hover:bg-white/10 transition-colors"
            >
              Get Started Free <ArrowUpRight className="w-4 h-4" />
            </motion.button>
          </Link>
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={handleWatchDemo}
            className="font-body font-medium text-sm text-white/80 flex items-center gap-2 hover:text-white transition-colors"
          >
            <Play className="w-4 h-4" /> Watch Demo
          </motion.button>
        </motion.div>
      </motion.div>
    </section>
  );
};

export default Hero;
