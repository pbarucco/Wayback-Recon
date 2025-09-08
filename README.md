# Wayback-Recon

A Burp Suite extension for passive recon using the Wayback Machine

![w](https://github.com/user-attachments/assets/dcf1421b-09fb-48f5-b086-669c57d4d8ee)

🔍 Overview

Wayback Recon integrates the Internet Archive Wayback Machine
into Burp Suite, letting you fetch and analyze historical URLs for a target domain directly inside Burp. This helps uncover forgotten endpoints, parameters, and assets that might still be useful for bug bounty or penetration testing.

✨ Features
✅ Fetch archived URLs from the Wayback Machine for any domain

✅ Display results in a sortable, searchable table

✅ Shows Year, URL, Length, and MIME-Type for each entry

✅ Built-in search filter for quick keyword hunting

✅ Right-click options:
   - Send to Sitemap (integrates with Burp’s Target tab)
   - Copy URLs to clipboard
   - Export results to a file
     
✅ Toggle “Add to sitemap” before fetching to automatically add all URLs to Burp’s Target sitemap

✅ Optional auto-add to Burp’s Sitemap while fetching

✅ Verbose log panel for debug and progress tracking

✅ Retry mechanism with backoff to handle network issues

🚀 Installation

In Burp Suite:
 - Go to Extender → Extensions → Add
 - Select Java and load the built wayback-filter-extension-1.jar

📖 Usage

1. Enter a target domain in the input field
2. Click Fetch to pull historical URLs from the Wayback Machine
3. Use the Search bar to quickly filter results
4. Right-click results for more options (Send to Sitemap, Copy, Export)

⚠️ Notes

 - By default, image MIME types are excluded to reduce noise
 - Only 200 OK responses are included
 - Duplicate URLs are collapsed to keep results clean
