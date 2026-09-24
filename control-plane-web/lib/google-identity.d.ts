export {};

// Minimal ambient shape for Google Identity Services' client-loaded global
// (https://accounts.google.com/gsi/client) - just the pieces the login page
// actually calls. No @types package exists for this; Google ships no types
// of its own for the plain script-tag API (only for the npm wrapper we're
// not using).
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }): void;
          renderButton(
            parent: HTMLElement,
            options: {
              type?: "standard" | "icon";
              theme?: "outline" | "filled_blue" | "filled_black";
              size?: "large" | "medium" | "small";
              width?: number | string;
              text?: "signin_with" | "signup_with" | "continue_with" | "signin";
            }
          ): void;
        };
      };
    };
  }
}
