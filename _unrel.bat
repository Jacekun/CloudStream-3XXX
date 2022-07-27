
set /p Tag=<doc/last_tag.txt
echo Last Tag is %Tag%
git log %Tag%..HEAD ^^upstream/master --reverse --oneline --invert-grep --grep="^\[skip ci\|^\Merge branch" --pretty=format:"+ %%s (%%aN)" > doc/unreleased.md
