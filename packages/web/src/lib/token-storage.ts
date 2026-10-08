const TOKEN_KEY = 'ft_token';
const REFRESH_KEY = 'ft_refresh_token';

export const tokenStorage = {
  getToken: (): string | null => localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_KEY),
  setRefreshToken: (token: string) => localStorage.setItem(REFRESH_KEY, token),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};
