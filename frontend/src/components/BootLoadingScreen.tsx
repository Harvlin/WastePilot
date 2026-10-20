type BootLoadingScreenProps = {
  progress: number;
};

const BootLoadingScreen = ({ progress }: BootLoadingScreenProps) => {
  const clampedProgress = Math.max(0, Math.min(100, progress));

  return (
    <div className="fixed inset-0 z-[200] bg-background flex items-center justify-center">
      <div className="w-[min(82vw,340px)] text-center space-y-5">
        <p className="text-muted-foreground font-body tracking-[0.08em] text-[22px] md:text-2xl">
          Loading {clampedProgress}%
        </p>

        <div className="h-[4px] w-full rounded-full bg-muted overflow-hidden">
          <div
            className="h-full rounded-full bg-brand transition-[width] duration-150 ease-out"
            style={{ width: `${clampedProgress}%` }}
          />
        </div>
      </div>
    </div>
  );
};

export default BootLoadingScreen;
