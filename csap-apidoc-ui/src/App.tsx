import LayoutIndex from "@/layouts";
import { EnvironmentProvider } from "@/contexts/EnvironmentContext";
import { HeadersProvider } from "@/contexts/HeadersContext";

const App = () => {
  return (
    <EnvironmentProvider>
      <HeadersProvider>
        <LayoutIndex />
      </HeadersProvider>
    </EnvironmentProvider>
  );
};

export default App;
