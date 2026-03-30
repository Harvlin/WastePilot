import Navbar from "@/components/Navbar";
import Hero from "@/components/Hero";
import MissionSection from "@/components/MissionSection";
import StartSection from "@/components/StartSection";
import DemoExplainerSection from "@/components/DemoExplainerSection";
import FeaturesChess from "@/components/FeaturesChess";
import FeaturesGrid from "@/components/FeaturesGrid";
import SolutionSection from "@/components/SolutionSection";
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
      <div className="mt-10 md:mt-14 space-y-10 md:space-y-14">
        <DemoExplainerSection />
        <FeaturesChess />
        <FeaturesGrid />
        <SolutionSection />
        <Stats />
        <Testimonials />
        <Faq />
      </div>
      <MissionSection />
      <CtaFooter />
    </div>
  );
};

export default Index;
