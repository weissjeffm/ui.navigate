# ui.navigate

A utility that helps break down navigation of a user interface into composable steps.

### Example

```clj
(def my-ui
  (page-zip
   (nav-tree [:top-level (fn [& _] (browser/open "/"))
              [:admin-page (fn [& _] (browser/click "Admin"))
               [:admin-users-page (fn [& _] (browser/click "Users"))
                [:edit-user-page (fn [user] (browser/click user))]]
               [:admin-settings-page (fn [& _] (browser/click "Settings"))]]])))
```

The above example represents a user interface where there is an `Admin` link on the main page, and then the `Admin` page has a link to `Users` and `Settings`. In turn the `Users` page has a list of users, where you can click on that user to edit his information. Note that only the `:edit-user-page has arguments that are used (the name of the user to click on).

After the pages are laid out as above, you can navigate to any page, just by specifying its identifier and any necessary arguments. For example:

```clj
(navigate nil :edit-user-page my-ui ["Joe"])

;; or

(navigate nil :admin-settings-page my-ui [])

;; or when already in a known state you can avoid starting from the top level
;; (say we know we're already on the :admin-page

(navigate :admin-page :edit-user-page my-ui ["Joe"])

```

Note that it is probably best to wrap the `navigate` function with your own function that doesn't require nils and empty lists to be passed when doing simple navigation.

# Building Page Trees

It is possible to build up a navigation tree from subtrees.

Let's say you define two different nav-tree areas of your UI, `admin` and `main`. (Note, these pieces of data should just be what's returned from `nav-tree`, don't create a zipper structre with `page-zip` until after the pieces are assembled)

```clj
(add-subnav-multiple [:top-level (fn [& _] (browser/open "/"))]
                     :top-level
                     [admin main])
```
                     
Here you pass the tree with branches missing, the place to attach the branches, and a list of branches. Note - if you need to attach branches at different places, you should make multiple calls to `add-subnav-multiple`, one for each attachment point. However you can attach many branches at one attachment point in a single call.
