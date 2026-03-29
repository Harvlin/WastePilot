import { useRef, useEffect } from "react";
import Hls from "hls.js";

interface HlsVideoProps {
  src: string;
  className?: string;
  style?: React.CSSProperties;
}

const HlsVideo = ({ src, className = "", style }: HlsVideoProps) => {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(src);
      hls.attachMedia(video);
      return () => hls.destroy();
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = src;
    }
  }, [src]);

  return (
    <video
      ref={videoRef}
      className={className}
      style={style}
      autoPlay
      loop
      muted
      playsInline
    />
  );
};

export default HlsVideo;
