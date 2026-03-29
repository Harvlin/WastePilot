import Navbar from "@/components/Navbar";
import Hero from "@/components/Hero";
import StartSection from "@/components/StartSection";
import DemoExplainerSection from "@/components/DemoExplainerSection";
import FeaturesChess from "@/components/FeaturesChess";
import FeaturesGrid from "@/components/FeaturesGrid";
import Stats from "@/components/Stats";
import Testimonials from "@/components/Testimonials";
import Faq from "@/components/Faq";
import CtaFooter from "@/components/CtaFooter";

const Index = () => {
  return (
    <div className="bg-black overflow-visible">
      <Navbar />
      <Hero />
      <StartSection />
      <DemoExplainerSection />
      <FeaturesChess />
      <FeaturesGrid />
      <Stats />
      <Testimonials />
      <Faq />
      <CtaFooter />
    </div>
  );
};

export default Index;
