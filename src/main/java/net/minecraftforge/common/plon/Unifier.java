package net.minecraftforge.common.plon;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import net.minecraftforge.common.util.DisjointSet;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/31/16.
 */
public class Unifier
{
    private final DisjointSet<AST.ISExp> union = new DisjointSet<AST.ISExp>();
    // type vars are globally unique so this should be good
    // TODO: check
    private final java.util.Map<Symbol, ISExp> boundVars = Maps.newHashMap();
    private int nextVar = 0;
    ISExp getFreshVar()
    {
        ISExp symbol = makeSymbol("T" + nextVar++);
        union.makeSet(symbol);
        return symbol;
    }

    void unify(ISExp first, ISExp second)
    {
        ISExp firstLink = find(first);
        ISExp secondLink = find(second);
        if(firstLink instanceof Symbol)
        {
            union((Symbol) firstLink, second);
            return;
        }
        else if(secondLink instanceof Symbol)
        {
            union((Symbol) secondLink, first);
            return;
        }
        else if(firstLink instanceof Cons && secondLink == PrimTypes.Map.type)
        {
            Cons type = (Cons) firstLink;
            if(length(type) == 2)
            {
                unify(type.car, PrimTypes.String.type);
                return;
            }
        }
        else if(firstLink instanceof IList && secondLink instanceof IList)
        {
            IList firstAbs = (IList) firstLink;
            IList secondAbs = (IList) secondLink;
            while(firstAbs != Nil.INSTANCE && secondAbs != Nil.INSTANCE)
            {
                @SuppressWarnings("ConstantConditions")
                Cons c1 = (Cons) firstAbs;
                Cons c2 = (Cons) secondAbs;
                unify(c1.car, c2.car);
                firstAbs = c1.cdr;
                secondAbs = c2.cdr;
            }
            if(firstAbs == secondAbs) // == Nil.INSTANCE
            {
                return;
            }
        }
        else if(firstLink.equals(secondLink))
        {
            return;
        }
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
        throw new IllegalStateException("Type error: can't unify " + typeToString(firstLink, typeMap) + " and " + typeToString(secondLink, typeMap));
    }

    // TODO: rethink this and disjoint set
    ISExp find(ISExp type)
    {
        if(type instanceof Symbol)
        {
            Symbol var = (Symbol) type;
            if (boundVars.containsKey(var))
            {
                ISExp to = boundVars.get(var);
                if(!type.equals(to))
                {
                    ISExp root = find(to);
                    if (to != root)
                    {
                        boundVars.put(var, root);
                    }
                    return root;
                }
            }
        }
        return type;
    }

    private boolean occurs(Symbol type, ISExp other)
    {
        ISExp link = find(other);
        if(link instanceof Symbol)
        {
            // FIXME: ==?
            return type.equals(link);
        }
        else if(link instanceof IList)
        {
            IList list = (IList) link;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                if(occurs(type, cons.car))
                {
                    return true;
                }
                list = cons.cdr;
            }
        }
        return false;
    }

    private void union(Symbol type, ISExp other)
    {
        if(!type.equals(other))
        {
            if(occurs(type, other))
            {
                ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
                throw new IllegalStateException("recursive type: " + typeToString(type, typeMap) + " = " + typeToString(other, typeMap));
            }
            // manually creating it cause it might be complex
            union.makeSet(other);
            union.union(type, other);
            // TODO: check
            boundVars.put(type, other);
        }
    }

    public boolean isBound(ISExp symbol)
    {
        // TODO: check
        return boundVars.containsKey(symbol);
    }

    public ImmutableMap<ISExp, ISExp> buildTypeMap()
    {
        ImmutableMap<ISExp, ImmutableSet<ISExp>> map = union.toMap();
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        for (ISExp from : map.keySet())
        {
            ISExp to = null;
            // can be faster since values have a lot of repeats
            for(ISExp candidate : map.get(from))
            {
                if(!(candidate instanceof Symbol))
                {
                    to = candidate;
                    break;
                }
            }
            if(to != null)
            {
                builder.put(from, to);
            }
        }
        return builder.build();
    }

    public String typeToString(ISExp type)
    {
        return typeToString(type, buildTypeMap());
    }

    public static String typeToString(ISExp type, ImmutableMap<ISExp, ISExp> typeMap)
    {
        if(type instanceof StringAtom)
        {
            return ((StringAtom) type).value;
        }
        else if(type instanceof Symbol)
        {
            if(typeMap.containsKey(type))
            {
                ISExp target = typeMap.get(type);
                if(type != target)
                {
                    return typeToString(target, typeMap);
                }
            }
            return ((Symbol) type).value;
        }
        else if(type == Unbound.INSTANCE)
        {
            return "&unbound";
        }
        Cons cons = (Cons)type;
        String r;
        if(length(type) == 2)
        {
            r = typeToString(cons.car, typeMap) + " -> ";
            cons = (Cons) cons.cdr;
        }
        else
        {
            r = "(";
            while(cons.cdr != Nil.INSTANCE)
            {
                r += typeToString(cons.car, typeMap);
                cons = (Cons) cons.cdr;
                if(cons.cdr != Nil.INSTANCE)
                {
                    r += ", ";
                }
            }
            r += ") -> ";
        }
        if(cons.car instanceof IList)
        {
            r += "(" + typeToString(cons.car, typeMap) + ")";
        }
        else
        {
            r += typeToString(cons.car, typeMap);
        }
        return r;
    }
}
