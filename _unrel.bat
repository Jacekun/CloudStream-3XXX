
set /p Tag=<doc/last_tag.txt
echo Last Tag is %Tag%
git --no-pager log %Tag%..HEAD --author="Jace" --invert-grep --grep="^\[skip ci" --pretty=format:"+ %%s" > doc/unreleased.md
