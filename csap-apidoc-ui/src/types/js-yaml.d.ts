declare module 'js-yaml' {
  export function dump(obj: any, options?: any): string;
  export function load(str: string, options?: any): any;
  export function loadAll(str: string, iterator: (doc: any) => void, options?: any): void;
  export function safeLoad(str: string, options?: any): any;
  export function safeLoadAll(str: string, iterator: (doc: any) => void, options?: any): void;
  export function safeDump(obj: any, options?: any): string;
  export default {
    dump,
    load,
    loadAll,
    safeLoad,
    safeLoadAll,
    safeDump
  };
}