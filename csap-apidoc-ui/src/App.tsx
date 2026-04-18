import LayoutIndex from "@/layouts";
import { EnvironmentProvider } from "@/contexts/EnvironmentContext";
import { HeadersProvider } from "@/contexts/HeadersContext";
import { AuthProvider } from "@/contexts/AuthContext";

const App = () => {
  return (
    <EnvironmentProvider>
      <HeadersProvider>
        <AuthProvider>
          <LayoutIndex />
        </AuthProvider>
      </HeadersProvider>
    </EnvironmentProvider>
  );
};

export default App;
