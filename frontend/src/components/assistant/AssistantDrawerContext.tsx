import { createContext, useContext } from 'react';

interface DrawerApi {
  /** Slide the assistant in, optionally with a question typed but not sent. */
  open: (prefill?: string) => void;
  close: () => void;
}

export const AssistantDrawerContext = createContext<DrawerApi>({ open: () => {}, close: () => {} });

export function useAssistantDrawer(): DrawerApi {
  return useContext(AssistantDrawerContext);
}
