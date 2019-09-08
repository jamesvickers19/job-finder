# job-finder

A small tool for finding remote jobs for Clojure, but could be adapted for other
languages.  Basically works by scraping a company list, then looking for the 'jobs'
page on each company site, then seeing if that page contains e.g. 'remote'.
The search results can be returned as a data structure organized by whether
the company site was found, whether it had a 'jobs' page, and whether the 'jobs'
page had 'remote'.

## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
