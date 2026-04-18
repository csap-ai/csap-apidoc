import LayoutIndex from "@/layouts";
import { EnvironmentProvider } from "@/contexts/EnvironmentContext";

const App = () => {
  return (
    <EnvironmentProvider>
      <LayoutIndex />
    </EnvironmentProvider>
  );
};

export default App;
