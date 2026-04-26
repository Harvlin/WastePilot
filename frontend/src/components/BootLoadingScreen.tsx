type BootLoadingScreenProps = {
  progress: number;
};

const BootLoadingScreen = ({ progress }: BootLoadingScreenProps) => {
  const clampedProgress = Math.max(0, Math.min(100, progress));

  return (
    <div className="fixed inset-0 z-[200] bg-black flex items-center justify-center">
      <div className="w-[min(82vw,340px)] text-center space-y-5">
        <p className="text-white/70 font-body tracking-[0.08em] text-[22px] md:text-2xl">
          Loading {clampedProgress}%
        </p>

        <div className="h-[4px] w-full rounded-full bg-white/20 overflow-hidden">
          <div
            className="h-full rounded-full bg-[hsl(var(--palette-tea-green))] shadow-[0_0_14px_hsl(var(--palette-tea-green)_/_0.45)] transition-[width] duration-150 ease-out"
            style={{ width: `${clampedProgress}%` }}
          />
        </div>
      </div>
    </div>
  );
};

export default BootLoadingScreen;
