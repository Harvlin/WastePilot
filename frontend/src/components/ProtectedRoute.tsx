import { Navigate, Outlet, useLocation } from "react-router-dom";
import { isMockAuthenticated } from "@/lib/mock-auth";

const ProtectedRoute = () => {
  const location = useLocation();

  if (!isMockAuthenticated()) {
    return <Navigate to="/auth" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
