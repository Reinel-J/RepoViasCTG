import { AuthProvider } from "../../context/AuthContext"
import { Layout } from "./Layout"

export function Root() {
  return (
    <AuthProvider>
      <Layout />
    </AuthProvider>
  )
}
