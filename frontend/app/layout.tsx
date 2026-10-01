import './globals.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'AskYourCode',
  description: 'Local codebase search and understanding',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
