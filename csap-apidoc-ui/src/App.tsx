import LayoutIndex from "@/layouts";
import { EnvironmentProvider } from "@/contexts/EnvironmentContext";
import { HeadersProvider } from "@/contexts/HeadersContext";
import { AuthProvider } from "@/contexts/AuthContext";
import { VaultProvider } from "@/contexts/VaultContext";

const App = () => {
  return (
    <VaultProvider>
      <EnvironmentProvider>
        <HeadersProvider>
          <AuthProvider>
            <LayoutIndex />
          </AuthProvider>
        </HeadersProvider>
      </EnvironmentProvider>
    </VaultProvider>
  );
};

export default App;
