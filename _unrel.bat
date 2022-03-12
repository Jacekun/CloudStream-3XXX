
set /p Tag=<doc/last_tag.txt
echo Last Tag is %Tag%
git --no-pager log %Tag%..HEAD --pretty=format:"+ %%s (%%aN)" > doc/unreleased.md --author="Jace"
