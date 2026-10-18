import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface AuthState {
  token: string | null;
  refreshToken: string | null;
  /** Stable user id: the avatar seed, so the face survives an email change. */
  id: string | null;
  email: string | null;
  role: string | null;
  /** Effective permissions (ADR-018); null = not known yet, so the UI shows everything and the server decides. */
  permissions: string[] | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

const initialState: AuthState = {
  token: null,
  refreshToken: null,
  id: null,
  email: null,
  role: null,
  permissions: null,
  isAuthenticated: false,
  isLoading: true,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials(state, action: PayloadAction<{
      token: string; refreshToken: string; id?: string | null; email: string; role: string; permissions?: string[] | null;
    }>) {
      state.token = action.payload.token;
      state.refreshToken = action.payload.refreshToken;
      state.id = action.payload.id ?? null;
      state.email = action.payload.email;
      state.role = action.payload.role;
      state.permissions = action.payload.permissions ?? null;
      state.isAuthenticated = true;
      state.isLoading = false;
    },
    logout(state) {
      state.token = null;
      state.refreshToken = null;
      state.id = null;
      state.email = null;
      state.role = null;
      state.permissions = null;
      state.isAuthenticated = false;
      state.isLoading = false;
    },
    setLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
  },
});

export const { setCredentials, logout, setLoading } = authSlice.actions;
export default authSlice.reducer;
