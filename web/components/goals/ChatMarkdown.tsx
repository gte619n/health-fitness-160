"use client";

import Markdown from "react-markdown";

// Minimal, safe markdown renderer for assistant chat text. react-markdown
// does not render raw HTML by default (no rehype-raw plugin), so input is
// safe. Styled with the Tesseta tokens — small type, olive links, muted
// code. Used only for assistant messages; user messages render as plain
// text.
export function ChatMarkdown({ children }: { children: string }) {
  return (
    <div className="space-y-2 text-[13px] leading-[1.55] text-primary [&_a]:text-accent-dim [&_a]:underline [&_code]:rounded [&_code]:bg-canvas-muted [&_code]:px-1 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-[11px] [&_h1]:text-[15px] [&_h1]:font-medium [&_h2]:text-[14px] [&_h2]:font-medium [&_h3]:text-[13px] [&_h3]:font-medium [&_li]:ml-4 [&_li]:list-disc [&_ol_li]:list-decimal [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:bg-canvas-muted [&_pre]:p-3 [&_strong]:font-medium [&_ul]:space-y-1">
      <Markdown
        components={{
          // Open links in a new tab; markdown links shouldn't yank the
          // user out of the chat thread.
          a: ({ ...props }) => (
            <a {...props} target="_blank" rel="noopener noreferrer" />
          ),
        }}
      >
        {children}
      </Markdown>
    </div>
  );
}
